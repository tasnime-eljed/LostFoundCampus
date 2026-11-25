package com.example.lostfoundcampus.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.ProfileRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.databinding.FragmentHomeBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.ui.detail.AnnouncementDetailFragment;
import com.example.lostfoundcampus.ui.publish.PublishFragment;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/** Fil des annonces actives : chargement paginé, filtres type / catégorie, recherche par mot-clé. */
public class HomeFragment extends Fragment {

    private static final String STATE_TAB = "tab";
    private static final String STATE_CATEGORY = "category";
    private static final long SEARCH_DEBOUNCE_MS = 400;

    private FragmentHomeBinding b;
    private AnnouncementAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int selectedTab = 0;
    @Nullable
    private Category selectedCategory;
    /** Incrémenté à chaque nouvelle recherche : les réponses d'une ancienne requête sont ignorées. */
    private int requestId;
    private int loadedCount;
    private boolean loading;
    private boolean endReached;

    private final Runnable debouncedRefresh = this::reload;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_TAB);
            int cat = savedInstanceState.getInt(STATE_CATEGORY, -1);
            selectedCategory = cat >= 0 ? Category.values()[cat] : null;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.appBar);
        b.avatarButton.setOnClickListener(v -> Nav.main(this).selectTab(R.id.profileFragment));
        bindMe(ProfileRepository.get(requireContext()).cachedMe());

        adapter = new AnnouncementAdapter(this::openDetail);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        b.list.setLayoutManager(lm);
        b.list.setAdapter(adapter);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || endReached) return;
                if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 4) loadPage(false);
            }
        });

        TabLayout.Tab tab = b.tabsType.getTabAt(selectedTab);
        if (tab != null) tab.select();
        b.tabsType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                selectedTab = t.getPosition();
                reload();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab t) {
            }
        });

        buildCategoryChips();

        b.etSearch.addTextChangedListener(new Nav.AfterTextChanged(s -> {
            handler.removeCallbacks(debouncedRefresh);
            handler.postDelayed(debouncedRefresh, SEARCH_DEBOUNCE_MS);
        }));
        b.etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(debouncedRefresh);
                reload();
                hideKeyboard();
                return true;
            }
            return false;
        });

        b.fabPublish.setOnClickListener(v -> openPublish());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Retour sur l'écran (publication, restitution…) : on recharge le fil et le profil.
        reload();
        ProfileRepository.get(requireContext()).loadMe(new ResultCallback<User>() {
            @Override
            public void onSuccess(User me) {
                bindMe(me);
            }

            @Override
            public void onError(String message) {
                // Le fil reste utilisable sans le profil.
            }
        });
    }

    private void bindMe(@Nullable User me) {
        if (b == null || me == null) return;
        b.txtGreeting.setText(getString(R.string.home_greeting, me.firstName()));
        UiUtils.bindAvatar(b.txtAvatar, b.imgAvatar, me);
    }

    private void buildCategoryChips() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        b.chipsCategory.removeAllViews();
        for (Category category : Category.values()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_category_chip, b.chipsCategory, false);
            chip.setId(View.generateViewId());
            chip.setText(category.labelRes);
            chip.setTag(category);
            chip.setChecked(category == selectedCategory);
            b.chipsCategory.addView(chip);
        }
        b.chipsCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            selectedCategory = null;
            if (!checkedIds.isEmpty()) {
                View checked = group.findViewById(checkedIds.get(0));
                if (checked != null) selectedCategory = (Category) checked.getTag();
            }
            reload();
        });
    }

    private void reload() {
        if (b == null) return;
        loadPage(true);
    }

    private void loadPage(boolean first) {
        if (b == null) return;
        final int myRequest = first ? ++requestId : requestId;
        loading = true;
        if (first && adapter.getItemCount() == 0) {
            b.progress.setVisibility(View.VISIBLE);
            b.empty.getRoot().setVisibility(View.GONE);
        }
        AnnouncementType type = selectedTab == 1 ? AnnouncementType.LOST
                : selectedTab == 2 ? AnnouncementType.FOUND : null;
        AnnouncementRepository.get(requireContext()).feed(type, selectedCategory, Nav.text(b.etSearch),
                first ? 0 : loadedCount, new ResultCallback<List<Announcement>>() {
                    @Override
                    public void onSuccess(List<Announcement> page) {
                        if (b == null || myRequest != requestId) return;
                        loading = false;
                        b.progress.setVisibility(View.GONE);
                        if (first) adapter.submit(page);
                        else adapter.append(page);
                        // Remis à zéro seulement en cas de succès : sinon un échec ferait ré-ajouter la page 1.
                        loadedCount = first ? page.size() : loadedCount + page.size();
                        endReached = page.size() < AnnouncementRepository.PAGE_SIZE;
                        renderEmpty(null);
                    }

                    @Override
                    public void onError(String message) {
                        if (b == null || myRequest != requestId) return;
                        loading = false;
                        b.progress.setVisibility(View.GONE);
                        if (adapter.getItemCount() == 0) renderEmpty(message);
                        else Nav.main(HomeFragment.this).showMessage(message);
                    }
                });
    }

    /** État vide (aucun résultat) ou état d'erreur (avec « Réessayer »). */
    private void renderEmpty(@Nullable String error) {
        boolean empty = adapter.getItemCount() == 0;
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.empty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        b.empty.btnEmptyAction.setVisibility(View.VISIBLE);
        if (error != null) {
            b.empty.imgEmpty.setImageResource(R.drawable.ic_map_off);
            b.empty.txtEmptyTitle.setText(R.string.state_error_title);
            b.empty.txtEmptyBody.setText(error);
            b.empty.btnEmptyAction.setText(R.string.action_retry);
            b.empty.btnEmptyAction.setOnClickListener(v -> reload());
        } else {
            b.empty.imgEmpty.setImageResource(R.drawable.ic_search_off);
            b.empty.txtEmptyTitle.setText(R.string.home_empty_title);
            b.empty.txtEmptyBody.setText(R.string.home_empty_body);
            b.empty.btnEmptyAction.setText(R.string.home_empty_action);
            b.empty.btnEmptyAction.setOnClickListener(v -> openPublish());
        }
    }

    private void openDetail(Announcement a) {
        Nav.go(this, R.id.announcementDetailFragment, AnnouncementDetailFragment.args(a.id));
    }

    private void openPublish() {
        Nav.go(this, R.id.publishFragment, PublishFragment.argsNew(AnnouncementType.LOST));
    }

    private void hideKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(b.etSearch.getWindowToken(), 0);
        b.etSearch.clearFocus();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
        outState.putInt(STATE_CATEGORY, selectedCategory == null ? -1 : selectedCategory.ordinal());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        b = null;
    }
}
