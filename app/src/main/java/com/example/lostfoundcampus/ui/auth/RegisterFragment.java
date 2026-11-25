package com.example.lostfoundcampus.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lostfoundcampus.MainActivity;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.databinding.FragmentRegisterBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.Validators;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentRegisterBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyPadding(b.scroll, true, true, true);
        b.header.txtTitle.setText(R.string.register_toolbar);
        b.header.btnBack.setOnClickListener(v -> Nav.back(this));
        b.btnSignIn.setOnClickListener(v -> Nav.back(this));
        b.btnSubmit.setOnClickListener(v -> submit());
        b.etConfirm.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        for (TextInputLayout til : new TextInputLayout[]{b.tilName, b.tilEmail, b.tilPassword, b.tilConfirm}) {
            Nav.clearErrorOnEdit(til);
        }
    }

    private void submit() {
        String name = Nav.text(b.etName);
        String email = Nav.text(b.etEmail);
        String password = b.etPassword.getText() == null ? "" : b.etPassword.getText().toString();
        String confirm = b.etConfirm.getText() == null ? "" : b.etConfirm.getText().toString();

        TextInputLayout firstError = null;
        if (Validators.isBlank(name)) {
            b.tilName.setError(getString(R.string.error_name_required));
            firstError = b.tilName;
        }
        if (!Validators.isUniversityEmail(email)) {
            b.tilEmail.setError(getString(R.string.error_email_university));
            if (firstError == null) firstError = b.tilEmail;
        }
        if (!Validators.isPasswordLongEnough(requireContext(), password)) {
            b.tilPassword.setError(getString(R.string.error_password_short));
            if (firstError == null) firstError = b.tilPassword;
        }
        if (!password.equals(confirm)) {
            b.tilConfirm.setError(getString(R.string.error_password_mismatch));
            if (firstError == null) firstError = b.tilConfirm;
        }
        if (firstError != null) {
            firstError.requestFocus();
            return;
        }

        setLoading(true);
        // Le profil (nom, e-mail universitaire) est créé côté base par le trigger handle_new_user.
        AuthRepository.get(requireContext()).signUp(name, email, password, new ResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean needsConfirmation) {
                if (b == null) return;
                setLoading(false);
                if (Boolean.TRUE.equals(needsConfirmation)) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.register_confirm_title)
                            .setMessage(getString(R.string.register_confirm_body, email))
                            .setPositiveButton(R.string.action_ok, (d, w) -> Nav.back(RegisterFragment.this))
                            .setCancelable(false)
                            .show();
                } else {
                    MainActivity main = Nav.main(RegisterFragment.this);
                    main.showMain();
                    main.showMessage(R.string.register_success);
                }
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                setLoading(false);
                if (message.equals(getString(R.string.error_email_university))
                        || message.equals(getString(R.string.error_user_exists))) {
                    b.tilEmail.setError(message);
                    b.tilEmail.requestFocus();
                } else if (message.equals(getString(R.string.error_password_short))) {
                    b.tilPassword.setError(message);
                } else {
                    Snackbar.make(b.getRoot(), message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        b.btnSubmit.setEnabled(!loading);
        b.btnSubmit.setText(loading ? R.string.register_loading : R.string.register_submit);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
