package com.example.lostfoundcampus.ui.common;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lostfoundcampus.MainActivity;
import com.google.android.material.textfield.TextInputLayout;

/** Raccourcis de navigation et de formulaires partagés par les écrans. */
public final class Nav {

    private Nav() {
    }

    public static NavController of(Fragment fragment) {
        return NavHostFragment.findNavController(fragment);
    }

    public static void go(Fragment fragment, @IdRes int destination, @Nullable Bundle args) {
        of(fragment).navigate(destination, args);
    }

    public static void back(Fragment fragment) {
        of(fragment).navigateUp();
    }

    public static MainActivity main(Fragment fragment) {
        return (MainActivity) fragment.requireActivity();
    }

    /** Efface l'erreur d'un champ dès que l'utilisateur le corrige. */
    public static void clearErrorOnEdit(TextInputLayout layout) {
        EditText edit = layout.getEditText();
        if (edit == null) return;
        edit.addTextChangedListener(new AfterTextChanged(s -> {
            if (layout.getError() != null) layout.setError(null);
        }));
    }

    public static String text(@Nullable EditText edit) {
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    /** TextWatcher réduit à afterTextChanged. */
    public static final class AfterTextChanged implements TextWatcher {
        public interface Listener {
            void onChanged(Editable s);
        }

        private final Listener listener;

        public AfterTextChanged(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            listener.onChanged(s);
        }
    }
}
