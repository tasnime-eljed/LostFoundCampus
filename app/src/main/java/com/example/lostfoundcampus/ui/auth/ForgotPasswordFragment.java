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
import com.example.lostfoundcampus.databinding.FragmentForgotPasswordBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.Validators;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Mot de passe oublié en deux temps, sans lien à ouvrir dans un navigateur :
 * 1) e-mail → Supabase envoie un code ; 2) code + nouveau mot de passe → session ouverte.
 */
public class ForgotPasswordFragment extends Fragment {

    private static final String STATE_EMAIL = "email";
    private static final String STATE_STEP2 = "step2";

    private FragmentForgotPasswordBinding b;
    private String email = "";
    private boolean step2;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            email = savedInstanceState.getString(STATE_EMAIL, "");
            step2 = savedInstanceState.getBoolean(STATE_STEP2);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyPadding(b.scroll, true, true, true);
        b.header.txtTitle.setText(R.string.forgot_toolbar);
        b.header.btnBack.setOnClickListener(v -> Nav.back(this));
        b.btnSend.setOnClickListener(v -> sendCode(false));
        b.btnResend.setOnClickListener(v -> sendCode(true));
        b.btnReset.setOnClickListener(v -> reset());
        b.etEmail.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sendCode(false);
                return true;
            }
            return false;
        });
        b.etConfirmPassword.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                reset();
                return true;
            }
            return false;
        });
        for (TextInputLayout til : new TextInputLayout[]{b.tilEmail, b.tilCode, b.tilNewPassword, b.tilConfirmPassword}) {
            Nav.clearErrorOnEdit(til);
        }
        showStep();
    }

    private void showStep() {
        b.groupForm.setVisibility(step2 ? View.GONE : View.VISIBLE);
        b.groupCode.setVisibility(step2 ? View.VISIBLE : View.GONE);
        if (step2) b.txtCodeBody.setText(getString(R.string.forgot_code_body, email));
    }

    private void sendCode(boolean resend) {
        if (!resend) email = Nav.text(b.etEmail);
        if (!Validators.isUniversityEmail(email)) {
            b.tilEmail.setError(getString(R.string.error_email_university));
            b.tilEmail.requestFocus();
            return;
        }
        View button = resend ? b.btnResend : b.btnSend;
        button.setEnabled(false);
        if (!resend) b.btnSend.setText(R.string.forgot_loading);
        AuthRepository.get(requireContext()).sendRecoveryCode(email, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (b == null) return;
                button.setEnabled(true);
                b.btnSend.setText(R.string.forgot_submit);
                step2 = true;
                showStep();
                if (resend) snack(getString(R.string.forgot_code_resent));
                b.etCode.requestFocus();
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                button.setEnabled(true);
                b.btnSend.setText(R.string.forgot_submit);
                snack(message);
            }
        });
    }

    private void reset() {
        String code = Nav.text(b.etCode);
        String password = raw(b.tilNewPassword);
        String confirm = raw(b.tilConfirmPassword);
        TextInputLayout firstError = null;
        if (code.length() < 6) {
            b.tilCode.setError(getString(R.string.error_code_required));
            firstError = b.tilCode;
        }
        if (!Validators.isPasswordLongEnough(requireContext(), password)) {
            b.tilNewPassword.setError(getString(R.string.error_password_short));
            if (firstError == null) firstError = b.tilNewPassword;
        }
        if (!password.equals(confirm)) {
            b.tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
            if (firstError == null) firstError = b.tilConfirmPassword;
        }
        if (firstError != null) {
            firstError.requestFocus();
            return;
        }
        b.btnReset.setEnabled(false);
        b.btnReset.setText(R.string.forgot_reset_loading);
        AuthRepository.get(requireContext()).resetPassword(email, code, password, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (b == null) return;
                MainActivity main = Nav.main(ForgotPasswordFragment.this);
                main.showMain();
                main.showMessage(R.string.forgot_reset_done);
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                b.btnReset.setEnabled(true);
                b.btnReset.setText(R.string.forgot_reset_submit);
                if (message.equals(getString(R.string.error_code_invalid))) {
                    b.tilCode.setError(message);
                } else if (message.equals(getString(R.string.error_password_short))) {
                    b.tilNewPassword.setError(message);
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

    private void snack(String message) {
        Snackbar.make(b.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_EMAIL, email);
        outState.putBoolean(STATE_STEP2, step2);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
