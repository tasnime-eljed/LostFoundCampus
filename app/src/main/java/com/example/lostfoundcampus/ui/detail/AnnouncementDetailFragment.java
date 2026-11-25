package com.example.lostfoundcampus.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavBackStackEntry;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lostfoundcampus.util.ColorNames;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ProfileRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.CampusLocation;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.databinding.FragmentAnnouncementDetailBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.ui.map.LocationPickerFragment;
import com.example.lostfoundcampus.ui.map.MapHelper;
import com.example.lostfoundcampus.ui.map.MiniMapController;
import com.example.lostfoundcampus.ui.matches.MatchesFragment;
import com.example.lostfoundcampus.ui.messages.ChatFragment;
import com.example.lostfoundcampus.util.DateUtil;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Détail d'une annonce, lu dans Supabase. « Voir les correspondances » et la modification du lieu
 * sont réservés à l'auteur ; « Contacter l'utilisateur » est masqué pour lui (RG02).
 */
public class AnnouncementDetailFragment extends Fragment {

    private static final String ARG_ID = "announcement_id";

    private FragmentAnnouncementDetailBinding b;
    private final PhotoPagerAdapter pagerAdapter = new PhotoPagerAdapter();
    @Nullable
    private TabLayoutMediator dotsMediator;
    @Nullable
    private MiniMapController miniMap;
    @Nullable
    private Announcement announcement;
    @Nullable
    private String announcementId;
    private boolean resumed;
    /** Vrai après une pause : au retour, on relit l'annonce (elle a pu être modifiée). */
    private boolean needsRefresh;

    public static Bundle args(String announcementId) {
        Bundle args = new Bundle();
        args.putString(ARG_ID, announcementId);
        return args;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAnnouncementDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTopMargin(b.btnBack);
        InsetsUtil.applyBottom(b.bottomBar, false);
        b.btnBack.setOnClickListener(v -> Nav.back(this));

        announcementId = getArguments() == null ? null : getArguments().getString(ARG_ID);
        if (announcementId == null) {
            Nav.of(this).popBackStack();
            return;
        }

        b.pager.setAdapter(pagerAdapter);
        b.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (b == null || announcement == null) return;
                b.pager.setContentDescription(getString(R.string.cd_gallery, position + 1,
                        announcement.photoPaths.size()));
            }
        });

        // Rien n'est affiché avant la réponse du serveur (évite un écran à moitié vide).
        b.scroll.setVisibility(View.INVISIBLE);
        b.bottomBar.setVisibility(View.GONE);
        b.cardAuthor.setVisibility(View.GONE);
        observeLocationResult();
        if (announcement != null) bind();
        load();
    }

    private void load() {
        if (announcementId == null) return;
        AnnouncementRepository.get(requireContext()).get(announcementId, new ResultCallback<Announcement>() {
            @Override
            public void onSuccess(Announcement a) {
                if (b == null) return;
                announcement = a;
                bind();
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                // Annonce introuvable (supprimée, archivée) ou réseau indisponible.
                Nav.main(AnnouncementDetailFragment.this).showMessage(announcement == null
                        && message.equals(getString(R.string.error_generic)) ? getString(R.string.detail_not_found) : message);
                if (announcement == null) Nav.of(AnnouncementDetailFragment.this).popBackStack();
            }
        });
    }

    private boolean isAuthor() {
        String me = AuthRepository.get(requireContext()).currentUserId();
        return announcement != null && me != null && me.equals(announcement.authorId);
    }

    private void bind() {
        Announcement a = announcement;
        if (a == null || b == null) return;
        boolean author = isAuthor();
        b.scroll.setVisibility(View.VISIBLE);

        // Galerie : indicateur de position seulement s'il y a plusieurs photos.
        pagerAdapter.submit(a.photoPaths, a.title);
        if (dotsMediator != null) dotsMediator.detach();
        dotsMediator = null;
        boolean several = a.photoPaths.size() > 1;
        b.dots.setVisibility(several ? View.VISIBLE : View.GONE);
        if (several) {
            dotsMediator = new TabLayoutMediator(b.dots, b.pager, (tab, position) -> { });
            dotsMediator.attach();
        }
        b.pager.setContentDescription(getString(R.string.cd_gallery, b.pager.getCurrentItem() + 1,
                Math.max(1, a.photoPaths.size())));

        UiUtils.bindTypeStatusBadge(b.txtBadge, a.type, a.status);
        b.txtTitle.setText(a.title);
        b.txtMeta.setText(getString(R.string.separator_dot, DateUtil.published(requireContext(), a.createdAt),
                getString(a.category.labelRes)));
        b.txtDescription.setText(a.description);
        b.txtDescription.setVisibility(a.hasDescription() ? View.VISIBLE : View.GONE);

        b.rowColor.txtInfoLabel.setText(R.string.detail_color);
        String colorText = ColorNames.display(a.color);
        b.rowColor.txtInfoValue.setText(colorText != null ? colorText : getString(R.string.color_unknown));
        b.rowLocation.txtInfoLabel.setText(R.string.detail_location);
        b.rowLocation.txtInfoValue.setText(MapHelper.label(requireContext(), a.location));
        b.rowDate.txtInfoLabel.setText(a.type == AnnouncementType.LOST ? R.string.detail_date_lost : R.string.detail_date_found);
        b.rowDate.txtInfoValue.setText(DateUtil.longDate(a.eventDate));

        // Mini-carte : modifiable par l'auteur tant que l'annonce est active, en lecture seule sinon.
        boolean canEdit = author && a.isActive();
        rebuildMiniMap(canEdit);
        b.btnEditLocation.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        b.btnEditLocation.setOnClickListener(v -> openLocationPicker());

        bindAuthor(a.authorId);

        boolean showContact = !author && a.isActive();
        boolean showMatches = author && a.isActive();
        b.btnContact.setVisibility(showContact ? View.VISIBLE : View.GONE);
        b.btnMatches.setVisibility(showMatches ? View.VISIBLE : View.GONE);
        b.bottomBar.setVisibility(showContact || showMatches ? View.VISIBLE : View.GONE);
        b.btnContact.setOnClickListener(v -> ChatFragment.openWithAuthor(this, a, v));
        b.btnMatches.setOnClickListener(v -> Nav.go(this, R.id.matchesFragment, MatchesFragment.args(a.id)));
    }

    private void bindAuthor(String authorId) {
        ProfileRepository profiles = ProfileRepository.get(requireContext());
        User cached = profiles.cached(authorId);
        if (cached != null) showAuthor(cached);
        profiles.loadUser(authorId, false, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                showAuthor(user);
            }

            @Override
            public void onError(String message) {
                // La carte auteur reste masquée.
            }
        });
    }

    private void showAuthor(User user) {
        if (b == null) return;
        UiUtils.bindInitialsAvatar(b.txtAuthorAvatar, user);
        b.txtAuthorName.setText(user.fullName);
        b.txtAuthorSubtitle.setText(R.string.detail_author_subtitle);
        b.cardAuthor.setVisibility(View.VISIBLE);
    }

    private void rebuildMiniMap(boolean editable) {
        if (miniMap != null) {
            if (resumed) miniMap.onPause();
            miniMap.onDestroy();
        }
        b.miniMap.mapContainer.removeAllViews();
        miniMap = new MiniMapController(b.miniMap, editable ? this::openLocationPicker : null);
        if (announcement != null) miniMap.show(announcement.location, MapHelper.defaultCenter(), null);
        if (resumed) miniMap.onResume();
    }

    private void openLocationPicker() {
        if (announcement == null) return;
        Nav.go(this, R.id.locationPickerFragment, LocationPickerFragment.args(announcement.location));
    }

    private void observeLocationResult() {
        NavBackStackEntry entry = Nav.of(this).getCurrentBackStackEntry();
        if (entry == null) return;
        entry.getSavedStateHandle().<CampusLocation>getLiveData(LocationPickerFragment.RESULT_KEY)
                .observe(getViewLifecycleOwner(), picked -> {
                    if (picked == null || announcement == null) return;
                    entry.getSavedStateHandle().remove(LocationPickerFragment.RESULT_KEY);
                    AnnouncementRepository.get(requireContext()).updateLocation(announcement.id, picked,
                            new ResultCallback<Announcement>() {
                                @Override
                                public void onSuccess(Announcement updated) {
                                    if (b == null) return;
                                    announcement = updated;
                                    bind();
                                    Nav.main(AnnouncementDetailFragment.this).showMessage(R.string.location_updated);
                                }

                                @Override
                                public void onError(String message) {
                                    if (b == null) return;
                                    Nav.main(AnnouncementDetailFragment.this).showMessage(message);
                                }
                            });
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        if (miniMap != null) miniMap.onResume();
        if (needsRefresh) load();
        needsRefresh = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        resumed = false;
        needsRefresh = true;
        if (miniMap != null) miniMap.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dotsMediator != null) dotsMediator.detach();
        dotsMediator = null;
        if (miniMap != null) miniMap.onDestroy();
        miniMap = null;
        b = null;
    }
}
