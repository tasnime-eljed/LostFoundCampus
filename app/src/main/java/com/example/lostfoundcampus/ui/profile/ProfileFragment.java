package com.example.lostfoundcampus.ui.profile;

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
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ProfileRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.databinding.FragmentProfileBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.ui.detail.AnnouncementDetailFragment;
import com.example.lostfoundcampus.ui.publish.PublishFragment;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** Mon profil : en-tête, mes annonces filtrables, « Marquer comme restituée », déconnexion. */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding b;
    private MyAnnouncementAdapter adapter;
    private AnnouncementRepository.ProfileFilter filter = AnnouncementRepository.ProfileFilter.ALL;
    /** Les réponses d'un ancien filtre sont ignorées. */
    private int requestId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.scroll);

        adapter = new MyAnnouncementAdapter(new MyAnnouncementAdapter.Listener() {
            @Override
            public void onOpen(Announcement a) {
                Nav.go(ProfileFragment.this, R.id.announcementDetailFragment, AnnouncementDetailFragment.args(a.id));
            }

            @Override
            public void onEdit(Announcement a) {
                Nav.go(ProfileFragment.this, R.id.publishFragment, PublishFragment.argsEdit(a.id));
            }

            @Override
            public void onMarkReturned(Announcement a) {
                confirmReturned(a);
            }
        });
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);

        b.chipsFilter.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chipAll : ids.get(0);
            if (id == R.id.chipLost) filter = AnnouncementRepository.ProfileFilter.LOST;
            else if (id == R.id.chipFound) filter = AnnouncementRepository.ProfileFilter.FOUND;
            else if (id == R.id.chipReturned) filter = AnnouncementRepository.ProfileFilter.RETURNED;
            else filter = AnnouncementRepository.ProfileFilter.ALL;
            loadList();
        });

        b.btnEditProfile.setOnClickListener(v -> Nav.go(this, R.id.editProfileFragment, null));
        b.btnLogout.setOnClickListener(v -> confirmLogout());

        User cached = ProfileRepository.get(requireContext()).cachedMe();
        if (cached != null) renderHeader(cached);
        else b.txtEmail.setText(AuthRepository.get(requireContext()).currentEmail());
    }

    @Override
    public void onResume() {
        super.onResume();
        ProfileRepository.get(requireContext()).loadMe(new ResultCallback<User>() {
            @Override
            public void onSuccess(User me) {
                renderHeader(me);
            }

            @Override
            public void onError(String message) {
                // L'en-tête garde la dernière valeur connue.
            }
        });
        loadList();
    }

    private void renderHeader(User me) {
        if (b == null) return;
        UiUtils.bindAvatar(b.avatar.txtInitials, b.avatar.imgPhoto, me);
        b.txtName.setText(me.fullName);
        b.txtEmail.setText(me.universityEmail);
    }

    private void loadList() {
        if (b == null) return;
        final int myRequest = ++requestId;
        AnnouncementRepository.get(requireContext()).mine(filter, new ResultCallback<List<Announcement>>() {
            @Override
            public void onSuccess(List<Announcement> data) {
                if (b == null || myRequest != requestId) return;
                adapter.submit(data);
                renderList(data.isEmpty(), null);
            }

            @Override
            public void onError(String message) {
                if (b == null || myRequest != requestId) return;
                if (adapter.getItemCount() == 0) renderList(true, message);
                else Nav.main(ProfileFragment.this).showMessage(message);
            }
        });
    }

    private void renderList(boolean empty, @Nullable String error) {
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.empty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        b.empty.btnEmptyAction.setVisibility(View.VISIBLE);
        if (error != null) {
            b.empty.imgEmpty.setImageResource(R.drawable.ic_map_off);
            b.empty.txtEmptyTitle.setText(R.string.state_error_title);
            b.empty.txtEmptyBody.setText(error);
            b.empty.btnEmptyAction.setText(R.string.action_retry);
            b.empty.btnEmptyAction.setOnClickListener(v -> loadList());
        } else {
            b.empty.imgEmpty.setImageResource(R.drawable.ic_inbox);
            b.empty.txtEmptyTitle.setText(R.string.profile_empty_title);
            b.empty.txtEmptyBody.setText(R.string.profile_empty_body);
            b.empty.btnEmptyAction.setText(R.string.profile_empty_action);
            b.empty.btnEmptyAction.setOnClickListener(v ->
                    Nav.go(this, R.id.publishFragment, PublishFragment.argsNew(AnnouncementType.LOST)));
        }
    }

    private void confirmReturned(Announcement a) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.returned_dialog_title)
                .setMessage(getString(R.string.returned_dialog_body, a.title))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (d, w) ->
                        AnnouncementRepository.get(requireContext()).markReturned(a.id, new ResultCallback<Announcement>() {
                            @Override
                            public void onSuccess(Announcement updated) {
                                if (b == null) return;
                                loadList();
                                Nav.main(ProfileFragment.this).showMessage(R.string.returned_done);
                            }

                            @Override
                            public void onError(String message) {
                                if (b == null) return;
                                Nav.main(ProfileFragment.this).showMessage(message);
                            }
                        }))
                .show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_dialog_title)
                .setMessage(R.string.logout_dialog_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.profile_logout, (d, w) -> {
                    AuthRepository.get(requireContext()).signOut();
                    Nav.main(this).showAuth();
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
