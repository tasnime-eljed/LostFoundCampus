package com.example.lostfoundcampus.ui.matches;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.Match;
import com.example.lostfoundcampus.databinding.FragmentMatchesBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.detail.AnnouncementDetailFragment;
import com.example.lostfoundcampus.ui.map.MapHelper;
import com.example.lostfoundcampus.ui.messages.ChatFragment;
import com.example.lostfoundcampus.util.DateUtil;
import com.example.lostfoundcampus.util.InsetsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Correspondances au-dessus du seuil de 60 %, calculées par la base (fonction match_candidates).
 * Le score est une indication, jamais une preuve (RG04).
 */
public class MatchesFragment extends Fragment {

    private static final String ARG_ID = "announcement_id";

    private FragmentMatchesBinding b;
    private MatchAdapter adapter;
    private int threshold;

    public static Bundle args(String announcementId) {
        Bundle args = new Bundle();
        args.putString(ARG_ID, announcementId);
        return args;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentMatchesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyPadding(b.scroll, true, true, false);
        b.header.txtTitle.setText(R.string.matches_title);
        b.header.btnBack.setOnClickListener(v -> Nav.back(this));
        b.cardMine.setOnClickListener(v -> Nav.back(this));
        threshold = getResources().getInteger(R.integer.lf_match_threshold);

        adapter = new MatchAdapter(new MatchAdapter.Listener() {
            @Override
            public void onView(Announcement a) {
                Nav.go(MatchesFragment.this, R.id.announcementDetailFragment, AnnouncementDetailFragment.args(a.id));
            }

            @Override
            public void onContact(Announcement a, View button) {
                ChatFragment.openWithAuthor(MatchesFragment.this, a, button);
            }
        });
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.list.setVisibility(View.GONE);
        b.txtFooter.setVisibility(View.GONE);
        b.empty.getRoot().setVisibility(View.GONE);
        b.txtCount.setText(R.string.loading);

        String id = getArguments() == null ? null : getArguments().getString(ARG_ID);
        if (id == null) {
            Nav.of(this).popBackStack();
            return;
        }
        load(id);
    }

    private void load(String id) {
        AnnouncementRepository repo = AnnouncementRepository.get(requireContext());
        repo.get(id, new ResultCallback<Announcement>() {
            @Override
            public void onSuccess(Announcement mine) {
                if (b == null) return;
                bindMine(mine);
                repo.matches(mine.id, threshold, new ResultCallback<List<Match>>() {
                    @Override
                    public void onSuccess(List<Match> matches) {
                        if (b == null) return;
                        loadCandidates(matches);
                    }

                    @Override
                    public void onError(String message) {
                        showError(id, message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                showError(id, message);
            }
        });
    }

    private void bindMine(Announcement mine) {
        ImageLoader.announcementPhoto(b.imgMine, mine.coverPath());
        b.imgMine.setContentDescription(getString(R.string.cd_announcement_photo, mine.title));
        b.txtMineLabel.setText(getString(R.string.matches_your_announcement, getString(mine.type.labelRes)));
        b.txtMineTitle.setText(mine.title);
        b.txtMineMeta.setText(getString(R.string.separator_dot, MapHelper.label(requireContext(), mine.location),
                DateUtil.shortDate(mine.eventDate)));
    }

    /** Récupère les annonces candidates en une requête, dans l'ordre des scores. */
    private void loadCandidates(List<Match> matches) {
        List<String> ids = new ArrayList<>();
        for (Match m : matches) ids.add(m.announcementId);
        AnnouncementRepository.get(requireContext()).byIds(ids, new ResultCallback<Map<String, Announcement>>() {
            @Override
            public void onSuccess(Map<String, Announcement> byId) {
                if (b == null) return;
                List<MatchAdapter.Item> items = new ArrayList<>();
                for (Match m : matches) {
                    Announcement a = byId.get(m.announcementId);
                    if (a != null) items.add(new MatchAdapter.Item(a, m.score));
                }
                render(items);
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                b.txtCount.setText("");
                Nav.main(MatchesFragment.this).showMessage(message);
            }
        });
    }

    private void render(List<MatchAdapter.Item> items) {
        adapter.submit(items);
        b.txtCount.setText(getResources().getQuantityString(R.plurals.matches_count, items.size(), items.size(), threshold));
        boolean empty = items.isEmpty();
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.txtFooter.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.empty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            b.empty.imgEmpty.setImageResource(R.drawable.ic_search);
            b.empty.txtEmptyTitle.setText(R.string.matches_empty_title);
            b.empty.txtEmptyBody.setText(R.string.matches_empty_body);
            b.empty.btnEmptyAction.setVisibility(View.GONE);
        }
    }

    private void showError(String id, String message) {
        if (b == null) return;
        b.txtCount.setText("");
        b.list.setVisibility(View.GONE);
        b.txtFooter.setVisibility(View.GONE);
        b.empty.getRoot().setVisibility(View.VISIBLE);
        b.empty.imgEmpty.setImageResource(R.drawable.ic_map_off);
        b.empty.txtEmptyTitle.setText(R.string.state_error_title);
        b.empty.txtEmptyBody.setText(message);
        b.empty.btnEmptyAction.setVisibility(View.VISIBLE);
        b.empty.btnEmptyAction.setText(R.string.action_retry);
        b.empty.btnEmptyAction.setOnClickListener(v -> {
            b.empty.getRoot().setVisibility(View.GONE);
            b.txtCount.setText(R.string.loading);
            load(id);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
