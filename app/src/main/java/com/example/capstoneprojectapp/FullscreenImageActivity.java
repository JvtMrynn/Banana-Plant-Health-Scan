package com.example.capstoneprojectapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class FullscreenImageActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private DetectionImageView fullscreenImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        toolbar = findViewById(R.id.toolbar);
        fullscreenImageView = findViewById(R.id.fullscreenImageView);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Full Image");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Load image and detections from holders
        android.graphics.Bitmap bmp = ImageHolder.getInstance().getImage();
        if (bmp != null) {
            // Ensure the view takes the bitmap's intrinsic size (no scaling)
            fullscreenImageView.setAdjustViewBounds(true);
            fullscreenImageView.setImageBitmap(bmp);
        }
        ArrayList<Detection> dets = DetectionHolder.getInstance().getDetections();
        if (dets != null && !dets.isEmpty()) {
            fullscreenImageView.setDetections(dets);
        }
    }
}
