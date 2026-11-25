package com.example.lostfoundcampus.ui.publish;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.databinding.FragmentCropBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.AppExecutors;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.PhotoCompressor;

/**
 * Cadrage de l'objet sur une photo, pour que l'analyse IA ignore la main, la table ou le fond.
 * Le cadre ne sert qu'à l'analyse : la photo publiée reste entière.
 */
public class CropFragment extends Fragment {

    private static final String ARG_URI = "uri";
    /** Taille d'affichage : la même que l'analyse, pour des coordonnées relatives identiques. */
    private static final int DISPLAY_MAX_SIDE = PhotoCompressor.ANALYSIS_MAX_SIDE;

    private FragmentCropBinding b;
    private PublishViewModel vm;
    private Uri uri;

    public static Bundle args(@NonNull Uri uri) {
        Bundle args = new Bundle();
        args.putString(ARG_URI, uri.toString());
        return args;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(PublishViewModel.class);
        String s = getArguments() == null ? null : getArguments().getString(ARG_URI);
        uri = s == null ? null : Uri.parse(s);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCropBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.topBar);
        InsetsUtil.applyBottom(b.actions, false);
        if (uri == null) {
            Nav.of(this).popBackStack();
            return;
        }
        b.btnClose.setOnClickListener(v -> Nav.of(this).popBackStack());
        b.btnFull.setOnClickListener(v -> b.cropView.resetToFullImage());
        b.btnAnalyzeZone.setOnClickListener(v -> analyzeZone());
        b.btnAnalyzeZone.setEnabled(false);

        RectF saved = vm.cropFor(uri);
        android.content.Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            Bitmap bmp;
            try {
                bmp = PhotoCompressor.decode(app, uri, DISPLAY_MAX_SIDE);
            } catch (Exception e) {
                bmp = null;
            }
            final Bitmap loaded = bmp;
            AppExecutors.main(() -> {
                if (b == null) return;
                if (loaded == null) {
                    Nav.main(this).showMessage(R.string.crop_error);
                    Nav.of(this).popBackStack();
                    return;
                }
                b.cropView.setImage(loaded, saved);
                b.btnAnalyzeZone.setEnabled(true);
            });
        });
    }

    private void analyzeZone() {
        RectF rel = b.cropView.getRelativeCrop();
        vm.setCrop(uri, rel == null || b.cropView.isFullImage() ? null : rel);
        Nav.go(this, R.id.aiAnalysisFragment, null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
