package com.example.lostfoundcampus.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.remote.SupabaseClient;
import com.example.lostfoundcampus.databinding.FragmentLoginBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.Validators;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentLoginBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyPadding(b.scroll, true, true, true);

        if (!SupabaseClient.isConfigured()) {
            showError(R.string.error_config_missing);
            b.btnLogin.setEnabled(false);
        }

        b.btnLogin.setOnClickListener(v -> attemptLogin());
        b.etPassword.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        b.btnForgot.setOnClickListener(v -> Nav.go(this, R.id.forgotPasswordFragment, null));
        b.btnRegister.setOnClickListener(v -> Nav.go(this, R.id.registerFragment, null));

        Nav.AfterTextChanged hideError = new Nav.AfterTextChanged(s -> b.txtError.setVisibility(View.GONE));
        b.etEmail.addTextChangedListener(hideError);
        b.etPassword.addTextChangedListener(hideError);
    }

    private void attemptLogin() {
        String email = Nav.text(b.etEmail);
        String password = b.etPassword.getText() == null ? "" : b.etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            showError(R.string.login_error_empty);
            return;
        }
        if (!Validators.isUniversityEmail(email)) {
            showError(R.string.login_error_credentials);
            return;
        }
        setLoading(true);
        AuthRepository.get(requireContext()).signIn(email, password, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (b == null) return;
                setLoading(false);
                Nav.main(LoginFragment.this).showMain();
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                setLoading(false);
                showError(message);
            }
        });
    }

    private void showError(@StringRes int message) {
        showError(getString(message));
    }

    private void showError(String message) {
        b.txtError.setText(message);
        b.txtError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        b.btnLogin.setEnabled(!loading);
        b.btnLogin.setText(loading ? R.string.login_loading : R.string.login_submit);
        b.etEmail.setEnabled(!loading);
        b.etPassword.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
