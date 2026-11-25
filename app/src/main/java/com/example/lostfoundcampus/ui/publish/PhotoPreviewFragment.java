package com.example.lostfoundcampus.ui.publish;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.databinding.FragmentPhotoPreviewBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

/** Aperçu plein cadre de la photo prise : « Analyser avec l'IA » ou « Reprendre la photo » (relance la caméra). */
public class PhotoPreviewFragment extends Fragment {

    private FragmentPhotoPreviewBinding b;
    private PublishViewModel vm;

    private final ActivityResultLauncher<Uri> retake =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                if (Boolean.TRUE.equals(saved) && vm.cameraTarget != null) {
                    vm.pendingPhoto = vm.cameraTarget;
                    vm.pendingCrop = null;
                    vm.cameraTarget = null;
                    if (b != null) showPhoto();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(PublishViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentPhotoPreviewBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.topBar);
        InsetsUtil.applyBottom(b.actions, false);

        if (vm.pendingPhoto == null) {
            Nav.of(this).popBackStack();
            return;
        }
        showPhoto();

        b.btnClose.setOnClickListener(v -> {
            vm.pendingPhoto = null;
            vm.pendingCrop = null;
            Nav.of(this).popBackStack();
        });
        b.btnAnalyze.setOnClickListener(v -> Nav.go(this, R.id.aiAnalysisFragment, null));
        b.btnCrop.setOnClickListener(v -> {
            if (vm.pendingPhoto != null) Nav.go(this, R.id.cropFragment, CropFragment.args(vm.pendingPhoto));
        });
        b.btnRetake.setOnClickListener(v -> {
            try {
                vm.cameraTarget = CameraFiles.newTarget(requireContext());
                retake.launch(vm.cameraTarget);
            } catch (IOException | ActivityNotFoundException e) {
                Snackbar.make(b.getRoot(), R.string.error_camera_unavailable, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showPhoto() {
        Glide.with(this).load(vm.pendingPhoto).fitCenter().into(b.imgPhoto);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
