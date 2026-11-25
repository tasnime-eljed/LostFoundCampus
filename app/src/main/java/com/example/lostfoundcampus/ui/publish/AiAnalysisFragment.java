package com.example.lostfoundcampus.ui.publish;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.ImageAnalysis;
import com.example.lostfoundcampus.databinding.FragmentAiAnalysisBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.InsetsUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay d'analyse : ML Kit (étiquettes, texte) et Palette (couleur) sur l'appareil, photo par photo.
 * Les résultats reviennent au formulaire comme suggestions modifiables (RG03).
 */
public class AiAnalysisFragment extends Fragment {

    private static final int[] STATUS_MESSAGES = {
            R.string.ai_status_detect, R.string.ai_status_text,
            R.string.ai_status_color, R.string.ai_status_ready
    };
    /** Durée minimale d'affichage, pour que l'overlay ne clignote pas sur un téléphone rapide. */
    private static final long MIN_DISPLAY_MS = 1500;

    private FragmentAiAnalysisBinding b;
    private PublishViewModel vm;
    private PhotoAnalyzer analyzer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Uri> queue = new ArrayList<>();
    private int statusIndex;
    private long startedAt;
    private boolean cancelled;
    private boolean anySuccess;

    private final Runnable nextStatus = new Runnable() {
        @Override
        public void run() {
            if (b == null) return;
            statusIndex = (statusIndex + 1) % STATUS_MESSAGES.length;
            b.txtStatus.setText(STATUS_MESSAGES[statusIndex]);
            handler.postDelayed(this, getResources().getInteger(R.integer.ai_status_interval_ms));
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(PublishViewModel.class);
        analyzer = new PhotoAnalyzer(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAiAnalysisBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyBottom(b.panel, false);

        queue.clear();
        queue.addAll(vm.analysisTargets());
        Uri preview = queue.isEmpty() ? null : queue.get(0);
        if (queue.isEmpty() || preview == null) {
            Nav.of(this).popBackStack();
            return;
        }
        Glide.with(this).load(preview).centerCrop().into(b.imgPhoto);
        b.btnCancel.setOnClickListener(v -> cancel());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        cancel();
                    }
                });

        b.aiScan.start();
        if (b.aiScan.isReducedMotion()) {
            b.txtStatus.setText(R.string.ai_status_static);
        } else {
            statusIndex = 0;
            b.txtStatus.setText(R.string.ai_status_prepare);
            handler.postDelayed(nextStatus, getResources().getInteger(R.integer.ai_status_interval_ms));
        }
        startedAt = SystemClock.elapsedRealtime();
        cancelled = false;
        anySuccess = false;
        analyzeNext();
    }

    private void analyzeNext() {
        if (cancelled || b == null) return;
        if (queue.isEmpty()) {
            finishWhenReady();
            return;
        }
        Uri uri = queue.remove(0);
        analyzer.analyze(uri, vm.cropFor(uri), new PhotoAnalyzer.Callback() {
            @Override
            public void onResult(@NonNull ImageAnalysis analysis) {
                if (cancelled) return;
                anySuccess = true;
                vm.attachAnalysis(uri, analysis, getResources().getInteger(R.integer.lf_max_photos));
                analyzeNext();
            }

            @Override
            public void onError() {
                if (cancelled) return;
                // La photo est gardée sans analyse : l'utilisateur remplit les champs lui-même.
                vm.attachAnalysis(uri, null, getResources().getInteger(R.integer.lf_max_photos));
                analyzeNext();
            }
        });
    }

    private void finishWhenReady() {
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        handler.postDelayed(() -> {
            if (cancelled || b == null) return;
            vm.applyBestSuggestions();
            if (!anySuccess) Nav.main(this).showMessage(R.string.ai_error);
            Nav.of(this).popBackStack(R.id.publishFragment, false);
        }, Math.max(0, MIN_DISPLAY_MS - elapsed));
    }

    /** Annuler arrête l'analyse et revient à l'écran précédent (aperçu ou formulaire), sans rien appliquer. */
    private void cancel() {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        Nav.of(this).popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        if (b != null) b.aiScan.stop();
        b = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        analyzer.close();
    }
}
