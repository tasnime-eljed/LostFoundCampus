package com.example.lostfoundcampus.ui.publish;

import java.util.ArrayList;
import com.google.android.material.chip.Chip;
import android.text.TextUtils;
import android.graphics.drawable.GradientDrawable;
import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavOptions;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.CampusLocation;
import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.data.model.DetectedColor;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.lostfoundcampus.data.model.ImageAnalysis;
import com.example.lostfoundcampus.data.remote.dto.PhotoAnalysisDto;
import com.example.lostfoundcampus.databinding.FragmentPublishBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.detail.AnnouncementDetailFragment;
import com.example.lostfoundcampus.ui.map.LocationHelper;
import com.example.lostfoundcampus.ui.map.LocationPickerFragment;
import com.example.lostfoundcampus.ui.map.MapHelper;
import com.example.lostfoundcampus.ui.map.MiniMapController;
import com.example.lostfoundcampus.util.DateUtil;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/** Publier une annonce : « J'ai perdu » (galerie), « J'ai trouvé » (caméra + position), et modification. */
public class PublishFragment extends Fragment {

    private static final String ARG_TYPE = "type";
    private static final String ARG_EDIT_ID = "edit_id";
    private static final String TAG_DATE_PICKER = "date_picker";
    private static final int MAX_PICK = 5;

    private FragmentPublishBinding b;
    private PublishViewModel vm;
    private PhotoThumbAdapter thumbs;
    private MiniMapController miniMap;
    /** Retour bloqué pendant l'envoi : l'annonce pourrait être enregistrée sans que l'utilisateur le sache. */
    private final OnBackPressedCallback blockBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (b != null) snack(getString(R.string.publish_in_progress));
        }
    };
    private boolean binding;
    private boolean loadingEdit;
    private boolean permissionAsked;

    // ---- Résultats d'activité : Photo Picker, appareil photo, permission de localisation
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotos =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK), this::onPhotosPicked);
    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);
    private final ActivityResultLauncher<String[]> askLocation =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> captureLocation());

    public static Bundle argsNew(AnnouncementType type) {
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        return args;
    }

    public static Bundle argsEdit(String announcementId) {
        Bundle args = new Bundle();
        args.putString(ARG_EDIT_ID, announcementId);
        return args;
    }

    private int maxPhotos() {
        return getResources().getInteger(R.integer.lf_max_photos);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(PublishViewModel.class);
        Bundle args = getArguments();
        String editId = args == null ? null : args.getString(ARG_EDIT_ID);
        // Écran recréé avant la fin du chargement, ou brouillon perdu (app tuée en arrière-plan) : on recharge.
        boolean editLost = editId != null && !(editId.equals(vm.editingId) && vm.editLoaded);
        if (savedInstanceState == null || editLost) {
            if (editId != null) {
                vm.reset(AnnouncementType.LOST);
                vm.editingId = editId;
                loadForEdit(editId);
            } else {
                String type = args == null ? null : args.getString(ARG_TYPE);
                vm.reset(type == null ? AnnouncementType.LOST : AnnouncementType.valueOf(type));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentPublishBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.scroll);
        InsetsUtil.applyBottom(b.bottomBar, true);

        b.header.txtTitle.setText(vm.isEditing() ? R.string.publish_edit_title : R.string.publish_title);
        b.header.btnBack.setOnClickListener(v -> {
            if (vm.publisher != null) snack(getString(R.string.publish_in_progress));
            else Nav.back(this);
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), blockBack);
        b.tabsType.setVisibility(vm.isEditing() ? View.GONE : View.VISIBLE);

        setUpTabs();
        setUpPhotos();
        setUpFields();
        miniMap = new MiniMapController(b.miniMap, this::openLocationPicker);
        b.btnAnalyze.setOnClickListener(v -> Nav.go(this, R.id.aiAnalysisFragment, null));
        b.btnPublish.setOnClickListener(v -> submit());

        observeLocationResult();
        render();
        ensureCapturedLocation();
        followPublisher();
    }

    // ------------------------------------------------------------------ Modification

    private void loadForEdit(String id) {
        loadingEdit = true;
        AnnouncementRepository repo = AnnouncementRepository.get(requireContext());
        repo.get(id, new ResultCallback<Announcement>() {
            @Override
            public void onSuccess(Announcement a) {
                if (isStaleEdit(id)) return;
                repo.analyses(id, new ResultCallback<List<PhotoAnalysisDto>>() {
                    @Override
                    public void onSuccess(List<PhotoAnalysisDto> analyses) {
                        if (isStaleEdit(id)) return;
                        vm.loadForEdit(a, analyses);
                        loadingEdit = false;
                        if (b != null) render();
                    }

                    @Override
                    public void onError(String message) {
                        if (isStaleEdit(id)) return;
                        vm.loadForEdit(a, Collections.<PhotoAnalysisDto>emptyList());
                        loadingEdit = false;
                        if (b != null) render();
                    }
                });
            }

            @Override
            public void onError(String message) {
                loadingEdit = false;
                if (b == null) return;
                Nav.main(PublishFragment.this).showMessage(message);
                Nav.back(PublishFragment.this);
            }
        });
    }

    /** Réponse tardive : l'écran est fermé ou le brouillon partagé sert déjà à autre chose. */
    private boolean isStaleEdit(String id) {
        return !isAdded() || !id.equals(vm.editingId) || vm.editLoaded;
    }

    // ------------------------------------------------------------------ Onglets

    private void setUpTabs() {
        TabLayout.Tab tab = b.tabsType.getTabAt(vm.type == AnnouncementType.FOUND ? 1 : 0);
        if (tab != null) tab.select();
        b.tabsType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                vm.setType(t.getPosition() == 1 ? AnnouncementType.FOUND : AnnouncementType.LOST);
                b.txtPhotoError.setVisibility(View.GONE);
                render();
                ensureCapturedLocation();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab t) {
            }
        });
    }

    // ------------------------------------------------------------------ Photos

    private void setUpPhotos() {
        thumbs = new PhotoThumbAdapter(new PhotoThumbAdapter.Listener() {
            @Override
            public void onRemove(int index) {
                vm.removePhoto(index);
                renderPhotos();
            }

            @Override
            public void onAdd() {
                addPhotos();
            }

            @Override
            public void onOpen(int index) {
                if (index < vm.photos.size() && vm.photos.get(index).localUri != null) {
                    openCrop(vm.photos.get(index).localUri);
                }
            }
        });
        b.rvPhotos.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        b.rvPhotos.setAdapter(thumbs);
        b.btnAddPhotos.setOnClickListener(v -> addPhotos());
        b.btnCrop.setOnClickListener(v -> {
            Uri first = vm.firstLocalPhoto();
            if (first != null) openCrop(first);
        });
    }

    private void openCrop(@NonNull Uri uri) {
        Nav.go(this, R.id.cropFragment, CropFragment.args(uri));
    }

    private void addPhotos() {
        if (vm.remainingSlots(maxPhotos()) == 0) return;
        b.txtPhotoError.setVisibility(View.GONE);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.publish_add_photo_title)
                .setItems(new CharSequence[]{
                        getString(R.string.publish_take_photo),
                        getString(R.string.publish_pick_gallery)
                }, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else {
                        launchGallery();
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            vm.cameraTarget = CameraFiles.newTarget(requireContext());
            takePicture.launch(vm.cameraTarget);
        } catch (IOException | ActivityNotFoundException e) {
            snack(getString(R.string.error_camera_unavailable));
        }
    }

    private void launchGallery() {
        pickPhotos.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void onPhotosPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        if (vm.type == AnnouncementType.FOUND && uris.size() == 1 && vm.photos.isEmpty()) {
            vm.pendingPhoto = uris.get(0);
            Nav.go(this, R.id.photoPreviewFragment, null);
        } else {
            vm.addLocalPhotos(uris, maxPhotos());
            if (b != null) renderPhotos();
        }
    }

    private void onPhotoTaken(Boolean saved) {
        if (Boolean.TRUE.equals(saved) && vm.cameraTarget != null) {
            vm.pendingPhoto = vm.cameraTarget;
            vm.cameraTarget = null;
            Nav.go(this, R.id.photoPreviewFragment, null);
        }
    }

    private void renderPhotos() {
        boolean found = vm.type == AnnouncementType.FOUND;
        int count = vm.photos.size();
        int max = maxPhotos();
        boolean full = count >= max;

        thumbs.submit(vm.photos, count > 0 && !full, found);
        b.rvPhotos.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        b.txtPhotoCount.setText(getString(R.string.publish_photos_count, count, max));
        if (full) {
            b.txtPhotosHint.setText(R.string.publish_photos_max);
        } else {
            b.txtPhotosHint.setText(found ? R.string.publish_photos_empty_found : R.string.publish_photos_empty_lost);
        }
        b.btnAddPhotos.setText(R.string.publish_add_photo_title);
        b.btnAddPhotos.setIconResource(found ? R.drawable.ic_camera : R.drawable.ic_gallery);
        b.btnAddPhotos.setVisibility(count == 0 ? View.VISIBLE : View.GONE);

        boolean toAnalyze = !vm.analysisTargets().isEmpty();
        b.groupAnalyze.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        b.txtAnalysisStatus.setText(!toAnalyze && vm.analysis != null
                ? R.string.publish_analysis_done : R.string.publish_analysis_none);
        b.btnAnalyze.setVisibility(toAnalyze ? View.VISIBLE : View.GONE);
        boolean canCrop = vm.firstLocalPhoto() != null;
        b.btnCrop.setVisibility(canCrop ? View.VISIBLE : View.GONE);
        b.txtCropHint.setVisibility(canCrop && !toAnalyze && vm.analysis != null ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ Analyse

    private void renderAnalysis() {
        ImageAnalysis a = vm.analysis;
        b.cardAnalysis.setVisibility(a == null ? View.GONE : View.VISIBLE);
        if (a == null) return;
        b.rowLabel.txtRowLabel.setText(R.string.analysis_label);
        b.rowLabel.txtRowValue.setText(getString(R.string.analysis_label_value, a.displayLabel, a.confidence));
        b.rowText.txtRowLabel.setText(R.string.analysis_text);
        String text = a.mainText();
        b.rowText.txtRowValue.setText(text != null ? text : getString(R.string.analysis_text_none));
        b.rowColor.txtRowLabel.setText(R.string.analysis_color);
        if (!a.colors.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (DetectedColor c : a.colors) names.add(c.name);
            b.rowColor.txtRowValue.setText(TextUtils.join(", ", names));
            b.rowColor.swatch.setVisibility(View.VISIBLE);
            ViewCompat.setBackgroundTintList(b.rowColor.swatch, ColorStateList.valueOf(a.colors.get(0).rgb));
        } else {
            b.rowColor.txtRowValue.setText(R.string.color_unknown);
            b.rowColor.swatch.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ Champs

    private void setUpFields() {
        String[] categories = new String[Category.values().length];
        for (int i = 0; i < categories.length; i++) categories[i] = getString(Category.values()[i].labelRes);
        b.actCategory.setSimpleItems(categories);
        b.actCategory.setOnItemClickListener((parent, v, position, id) -> {
            vm.category = Category.values()[position];
            b.tilCategory.setError(null);
            renderAiHelpers();
        });

        b.etColor.addTextChangedListener(new Nav.AfterTextChanged(s -> {
            if (binding) return;
            vm.color = s.toString();
            renderAiHelpers();
        }));

        b.etTitle.addTextChangedListener(new Nav.AfterTextChanged(s -> {
            if (binding) return;
            vm.title = s.toString();
            b.tilTitle.setError(null);
            renderAiHelpers();
        }));
        b.etDescription.addTextChangedListener(new Nav.AfterTextChanged(s -> {
            if (!binding) vm.description = s.toString();
        }));

        b.etDate.setOnClickListener(v -> openDatePicker());
        b.tilDate.setEndIconOnClickListener(v -> openDatePicker());
    }

    /** « Suggestion IA : à vérifier » tant que la valeur proposée n'a pas été modifiée. */
    private void renderAiHelpers() {
        String hint = getString(R.string.ai_suggestion_chip);
        b.tilTitle.setHelperText(vm.titleIsAiSuggestion() ? hint : null);
        b.tilCategory.setHelperText(vm.categoryIsAiSuggestion() ? hint : null);
        b.tilColor.setHelperText(vm.colorIsAiSuggestion() ? hint : null);
        renderColorChips();
    }

    /** Couleurs détectées sur la photo, avec leur teinte réelle : un appui remplit le champ. */
    private void renderColorChips() {
        b.chipsColors.removeAllViews();
        List<DetectedColor> detected = vm.analysis == null ? new ArrayList<>() : vm.analysis.colors;
        b.txtColorsDetected.setVisibility(detected.isEmpty() ? View.GONE : View.VISIBLE);
        b.chipsColors.setVisibility(detected.isEmpty() ? View.GONE : View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        for (DetectedColor c : detected) {
            Chip chip = new Chip(requireContext());
            chip.setText(c.name);
            chip.setCheckable(false);
            chip.setContentDescription(getString(R.string.cd_color_chip, c.name));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(c.rgb);
            dot.setStroke(Math.round(density), ContextCompat.getColor(requireContext(), R.color.lf_outline));
            dot.setSize(Math.round(16 * density), Math.round(16 * density));
            chip.setChipIcon(dot);
            chip.setChipIconVisible(true);
            chip.setOnClickListener(v -> {
                vm.color = c.name;
                renderFields();
            });
            b.chipsColors.addView(chip);
        }
    }

    private void renderFields() {
        binding = true;
        if (!Nav.text(b.etTitle).equals(vm.title.trim())) b.etTitle.setText(vm.title);
        if (!Nav.text(b.etDescription).equals(vm.description.trim())) b.etDescription.setText(vm.description);
        b.actCategory.setText(vm.category == null ? "" : getString(vm.category.labelRes), false);
        String color = vm.color == null ? "" : vm.color;
        if (!Nav.text(b.etColor).equals(color.trim())) b.etColor.setText(color);
        b.txtDateLabel.setText(vm.type == AnnouncementType.FOUND ? R.string.label_date_found : R.string.label_date_lost);
        b.etDate.setText(vm.date == null ? "" : DateUtil.longDate(vm.date));
        binding = false;
        renderAiHelpers();
    }

    private void openDatePicker() {
        if (getChildFragmentManager().findFragmentByTag(TAG_DATE_PICKER) != null) return;
        long selection = vm.date != null ? toUtcMidnight(vm.date) : MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(vm.type == AnnouncementType.FOUND
                        ? R.string.date_picker_title_found : R.string.date_picker_title_lost)
                .setSelection(selection)
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointBackward.now()).build())
                .build();
        picker.addOnPositiveButtonClickListener(utc -> {
            vm.date = fromUtcMidnight(utc);
            b.tilDate.setError(null);
            renderFields();
        });
        picker.show(getChildFragmentManager(), TAG_DATE_PICKER);
    }

    /** Le sélecteur Material travaille en minuit UTC ; on convertit vers le jour local. */
    private static long fromUtcMidnight(long utc) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utc);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0);
        return local.getTimeInMillis();
    }

    private static long toUtcMidnight(long local) {
        Calendar l = Calendar.getInstance();
        l.setTimeInMillis(local);
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH));
        return u.getTimeInMillis();
    }

    // ------------------------------------------------------------------ Lieu

    /** « J'ai trouvé » : le lieu est prérempli avec la position actuelle. */
    private void ensureCapturedLocation() {
        if (!vm.needsCapturedLocation() || vm.locating) return;
        if (LocationHelper.hasPermission(requireContext())) {
            captureLocation();
        } else if (!permissionAsked) {
            permissionAsked = true;
            askLocation.launch(LocationHelper.PERMISSIONS);
        }
    }

    private void captureLocation() {
        if (!vm.needsCapturedLocation()) return;
        if (!LocationHelper.hasPermission(requireContext())) {
            if (b != null) miniMap.show(null, MapHelper.defaultCenter(), getString(R.string.location_permission_denied));
            return;
        }
        vm.locating = true;
        renderLocation();
        LocationHelper.current(requireContext(), new LocationHelper.Callback() {
            @Override
            public void onLocation(@NonNull CampusLocation location) {
                vm.locating = false;
                if (vm.needsCapturedLocation()) vm.setCapturedLocation(location);
                if (b != null) renderLocation();
            }

            @Override
            public void onUnavailable(int reason) {
                vm.locating = false;
                if (b != null) miniMap.show(null, MapHelper.defaultCenter(), getString(reason));
            }
        });
    }

    private void openLocationPicker() {
        Nav.go(this, R.id.locationPickerFragment, LocationPickerFragment.args(vm.location));
    }

    private void observeLocationResult() {
        NavBackStackEntry entry = Nav.of(this).getCurrentBackStackEntry();
        if (entry == null) return;
        entry.getSavedStateHandle().<CampusLocation>getLiveData(LocationPickerFragment.RESULT_KEY)
                .observe(getViewLifecycleOwner(), picked -> {
                    if (picked == null) return;
                    vm.setLocation(picked);
                    entry.getSavedStateHandle().remove(LocationPickerFragment.RESULT_KEY);
                    b.txtLocationError.setVisibility(View.GONE);
                    renderLocation();
                });
    }

    private void renderLocation() {
        if (vm.locating && vm.location == null) {
            miniMap.show(null, MapHelper.defaultCenter(), getString(R.string.location_searching));
            return;
        }
        String override = vm.locationCaptured && vm.location != null && !vm.location.hasPrecision()
                ? getString(R.string.map_captured_position) : null;
        miniMap.show(vm.location, MapHelper.defaultCenter(), override);
    }

    // ------------------------------------------------------------------ Rendu global

    private void render() {
        b.btnPublish.setEnabled(!loadingEdit && vm.publisher == null);
        b.btnPublish.setText(loadingEdit ? getString(R.string.loading)
                : getString(vm.isEditing() ? R.string.publish_save : R.string.publish_submit));
        renderPhotos();
        renderAnalysis();
        renderFields();
        renderLocation();
    }

    // ------------------------------------------------------------------ Publication

    private void submit() {
        vm.title = Nav.text(b.etTitle);
        vm.description = Nav.text(b.etDescription);
        View firstError = null;

        if (vm.photos.isEmpty()) {
            b.txtPhotoError.setText(R.string.error_photo_required);
            b.txtPhotoError.setVisibility(View.VISIBLE);
            firstError = b.cardPhotos;
        }
        if (vm.title.isEmpty()) {
            b.tilTitle.setError(getString(R.string.error_item_name));
            if (firstError == null) firstError = b.tilTitle;
        }
        if (vm.category == null) {
            b.tilCategory.setError(getString(R.string.error_category));
            if (firstError == null) firstError = b.tilCategory;
        }
        if (vm.location == null) {
            b.txtLocationError.setVisibility(View.VISIBLE);
            if (firstError == null) firstError = b.miniMap.getRoot();
        }
        if (vm.date == null) {
            b.tilDate.setError(getString(R.string.error_date));
            if (firstError == null) firstError = b.tilDate;
        }
        if (firstError != null) {
            final View target = firstError;
            b.scroll.post(() -> b.scroll.smoothScrollTo(0, Math.max(0, target.getTop() - b.content.getPaddingTop())));
            Snackbar.make(b.getRoot(), R.string.error_form_summary, Snackbar.LENGTH_SHORT)
                    .setAnchorView(b.bottomBar).show();
            return;
        }

        if (vm.publisher != null) return;
        b.btnPublish.setEnabled(false);
        b.btnPublish.setText(R.string.publish_loading);
        vm.publisher = new AnnouncementPublisher(requireContext(), vm);
        vm.publisher.start();
        followPublisher();
    }

    /** Suit l'envoi en cours (aussi après une rotation) et empêche de quitter l'écran avant la fin. */
    private void followPublisher() {
        blockBack.setEnabled(vm.publisher != null);
        if (vm.publisher == null) return;
        vm.publisher.setListener(new AnnouncementPublisher.Listener() {
            @Override
            public void onProgress(String message) {
                if (b != null && !message.isEmpty()) b.btnPublish.setText(message);
            }

            @Override
            public void onDone(@NonNull String announcementId) {
                blockBack.setEnabled(false);
                if (b != null) showPublished();
            }

            @Override
            public void onError(@NonNull String message) {
                blockBack.setEnabled(false);
                if (b == null) return;
                render();
                snack(message);
            }
        });
    }

    private void showPublished() {
        String id = vm.publishedId;
        // App en arrière-plan : la navigation serait ignorée, onResume() reprendra.
        if (id == null || isStateSaved()) return;
        boolean editing = vm.isEditing();
        vm.reset(vm.type);
        NavOptions options = new NavOptions.Builder().setPopUpTo(R.id.publishFragment, true).build();
        Nav.of(this).navigate(R.id.announcementDetailFragment, AnnouncementDetailFragment.args(id), options);
        Nav.main(this).showMessage(editing ? R.string.publish_updated : R.string.publish_success);
    }

    private void snack(String message) {
        Snackbar.make(b.getRoot(), message, Snackbar.LENGTH_LONG).setAnchorView(b.bottomBar).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        miniMap.onResume();
        // Envoi terminé pendant que l'écran était masqué.
        if (vm.publishedId != null) b.getRoot().post(() -> {
            if (b != null) showPublished();
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        miniMap.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        miniMap.onDestroy();
        if (vm.publisher != null) vm.publisher.setListener(null);
        b = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Rotation : l'envoi continue dans le ViewModel. Sinon l'écran est vraiment fermé.
        if (vm.publisher != null && !requireActivity().isChangingConfigurations()) {
            vm.publisher.cancel();
            vm.publisher = null;
        }
    }
}
