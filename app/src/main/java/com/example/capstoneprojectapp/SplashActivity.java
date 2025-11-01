package com.example.capstoneprojectapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Trigger a background model update check on app start
        YOLOv8DetectionService detectionService = new YOLOv8DetectionService();
        detectionService.initialize(this);
        detectionService.checkAndUpdateModelAsync((updated, msg) -> { /* no UI here */ });

        // If offline at app start, route directly to offline farmer mode
        if (!com.example.capstoneprojectapp.util.NetworkUtils.isOnline(this)) {
            startActivity(new Intent(SplashActivity.this, FarmerDashboardActivity.class)
                    .putExtra("OFFLINE_MODE", true));
            finish();
            return;
        }

        // When online, warm the cache in background
        com.example.capstoneprojectapp.data.repo.DataRepository repo = new com.example.capstoneprojectapp.data.repo.DataRepository(this);
        new Thread(repo::getDiseaseInfoSync).start();

        // Navigate to Login after a short splash delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }, 1200);
    }
}
