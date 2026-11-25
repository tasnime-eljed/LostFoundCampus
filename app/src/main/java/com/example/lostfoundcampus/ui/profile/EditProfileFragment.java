package com.example.lostfoundcampus.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ProfileRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.StorageRepository;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.databinding.FragmentEditProfileBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.util.AppExecutors;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.PhotoCompressor;
import com.example.lostfoundcampus.util.Validators;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Modifier mon profil (accessible uniquement depuis son propre profil) :
 * nom, photo (Photo Picker, envoyée dans le bucket « avatars ») et mot de passe
 * (l'actuel est vérifié par une nouvelle connexion avant l'enregistrement du nouveau).
 */
public class EditProfileFragment extends Fragment {

    private static final String STATE_PHOTO = "pending_photo";

    private FragmentEditProfileBinding b;
    @Nullable
    private User me;
    /** Photo choisie, pas encore enregistrée. */
    @Nullable
    private Uri pendingPhoto;
    /** Contexte de l'application : utilisable dans les rappels même si l'écran est fermé entre-temps. */
    private android.content.Context app;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null || b == null) return;
                pendingPhoto = uri;
                renderAvatar();
                snack(R.string.edit_profile_photo_changed);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = requireContext().getApplicationContext();
        if (savedInstanceState != null) pendingPhoto = BundleCompat.getParcelable(savedInstanceState, STATE_PHOTO, Uri.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentEditProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyPadding(b.scroll, true, true, true);
        b.header.txtTitle.setText(R.string.edit_profile_title);
        b.header.btnBack.setOnClickListener(v -> Nav.back(this));

        me = ProfileRepository.get(requireContext()).cachedMe();
        if (me != null) bindMe(me, savedInstanceState == null);
        else b.txtEmail.setText(AuthRepository.get(requireContext()).currentEmail());
        ProfileRepository.get(requireContext()).loadMe(new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                boolean fillName = me == null && savedInstanceState == null;
                me = user;
                bindMe(user, fillName);
            }

            @Override
            public void onError(String message) {
                if (b != null && me == null) snack(message);
            }
        });

        b.btnChangePhoto.setOnClickListener(v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        b.btnSaveProfile.setOnClickListener(v -> saveProfile());
        b.btnChangePassword.setOnClickListener(v -> changePassword());

        for (TextInputLayout til : new TextInputLayout[]{b.tilName, b.tilCurrent, b.tilNew, b.tilConfirm}) {
            Nav.clearErrorOnEdit(til);
        }
    }

    private void bindMe(User user, boolean fillName) {
        if (b == null) return;
        if (fillName) b.etName.setText(user.fullName);
        b.txtEmail.setText(user.universityEmail);
        renderAvatar();
    }

    private void renderAvatar() {
        if (b == null) return;
        if (pendingPhoto != null) {
            b.avatar.imgPhoto.setVisibility(View.VISIBLE);
            b.avatar.txtInitials.setVisibility(View.INVISIBLE);
            ImageLoader.localAvatar(b.avatar.imgPhoto, pendingPhoto);
        } else if (me != null) {
            UiUtils.bindAvatar(b.avatar.txtInitials, b.avatar.imgPhoto, me);
        }
    }

    // ------------------------------------------------------------------ Nom et photo

    private void saveProfile() {
        String name = Nav.text(b.etName);
        if (Validators.isBlank(name)) {
            b.tilName.setError(getString(R.string.error_name_required));
            b.tilName.requestFocus();
            return;
        }
        String uid = AuthRepository.get(requireContext()).currentUserId();
        if (uid == null) {
            snack(R.string.error_session_expired);
            return;
        }
        b.btnSaveProfile.setEnabled(false);
        b.btnSaveProfile.setText(R.string.edit_profile_saving);
        if (pendingPhoto == null) {
            saveRow(name, null, false);
            return;
        }
        Uri photo = pendingPhoto;
        AppExecutors.io(() -> {
            byte[] jpeg;
            try {
                jpeg = PhotoCompressor.compress(app, photo,
                        PhotoCompressor.AVATAR_MAX_SIDE, PhotoCompressor.AVATAR_MAX_BYTES);
            } catch (Exception e) {
                AppExecutors.main(() -> failSave(app.getString(R.string.edit_profile_photo_error)));
                return;
            }
            // Nom de fichier unique : l'ancienne image en cache (Glide) n'est jamais réaffichée.
            String path = uid + "/avatar-" + System.currentTimeMillis() + ".jpg";
            AppExecutors.main(() -> StorageRepository.get(app).upload(
                    StorageRepository.BUCKET_AVATARS, path, jpeg, false, new ResultCallback<String>() {
                        @Override
                        public void onSuccess(String uploaded) {
                            saveRow(name, uploaded, true);
                        }

                        @Override
                        public void onError(String message) {
                            failSave(message);
                        }
                    }));
        });
    }

    private void saveRow(String name, @Nullable String photoPath, boolean setPhoto) {
        final String previous = me == null ? null : me.photoPath;
        ProfileRepository.get(app).updateMe(name, photoPath, setPhoto, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (setPhoto && previous != null && !previous.equals(photoPath)) {
                    StorageRepository.get(app).deleteQuietly(StorageRepository.BUCKET_AVATARS, previous);
                }
                if (b == null) return;
                me = user;
                pendingPhoto = null;
                resetSaveButton();
                renderAvatar();
                snack(R.string.edit_profile_saved);
            }

            @Override
            public void onError(String message) {
                if (setPhoto && photoPath != null) {
                    StorageRepository.get(app).deleteQuietly(StorageRepository.BUCKET_AVATARS, photoPath);
                }
                failSave(message);
            }
        });
    }

    private void failSave(String message) {
        if (b == null) return;
        resetSaveButton();
        snack(message);
    }

    private void resetSaveButton() {
        b.btnSaveProfile.setEnabled(true);
        b.btnSaveProfile.setText(R.string.edit_profile_save);
    }

    // ------------------------------------------------------------------ Mot de passe

    private void changePassword() {
        String current = raw(b.tilCurrent);
        String next = raw(b.tilNew);
        String confirm = raw(b.tilConfirm);
        TextInputLayout firstError = null;

        if (current.isEmpty()) {
            b.tilCurrent.setError(getString(R.string.error_password_current_required));
            firstError = b.tilCurrent;
        }
        if (!Validators.isPasswordLongEnough(requireContext(), next)) {
            b.tilNew.setError(getString(R.string.error_password_short));
            if (firstError == null) firstError = b.tilNew;
        }
        if (!next.equals(confirm)) {
            b.tilConfirm.setError(getString(R.string.error_password_mismatch));
            if (firstError == null) firstError = b.tilConfirm;
        }
        if (firstError != null) {
            firstError.requestFocus();
            return;
        }

        b.btnChangePassword.setEnabled(false);
        AuthRepository.get(requireContext()).changePassword(current, next, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (b == null) return;
                b.etCurrent.setText("");
                b.etNew.setText("");
                b.etConfirm.setText("");
                b.btnChangePassword.setEnabled(true);
                snack(R.string.edit_profile_password_saved);
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                b.btnChangePassword.setEnabled(true);
                if (message.equals(getString(R.string.error_password_current))) {
                    b.tilCurrent.setError(message);
                    b.tilCurrent.requestFocus();
                } else {
                    snack(message);
                }
            }
        });
    }

    private static String raw(TextInputLayout til) {
        return til.getEditText() == null || til.getEditText().getText() == null
                ? "" : til.getEditText().getText().toString();
    }

    private void snack(@StringRes int message) {
        snack(getString(message));
    }

    private void snack(String message) {
        if (b == null) return;
        Snackbar.make(b.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_PHOTO, pendingPhoto);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
