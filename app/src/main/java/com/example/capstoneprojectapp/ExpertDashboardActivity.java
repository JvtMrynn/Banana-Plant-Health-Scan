package com.example.capstoneprojectapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ExpertDashboardActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView welcomeText, tvNotificationBadge;
    private MaterialCardView cardDiseaseInfo, cardModelInfo, cardAnalysisHistory, cardConsultationRequests;
    private BottomNavigationView bottomNavigation;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";
    private com.google.firebase.firestore.ListenerRegistration badgeListenerRegistration;

    // Detection UI and logic for experts
    private DetectionImageView expertImageView;
    private MaterialButton btnExpertCamera, btnExpertGallery, btnExpertAnalyze, btnExpertClear;
    private Bitmap selectedImage;
    private YOLOv8DetectionService detectionService;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expert_dashboard);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        initializeActivityLaunchers();
        setupClickListeners();
        updateUserInfo();
        loadPendingRequestsCount();

        detectionService = new YOLOv8DetectionService();
        detectionService.initialize(this);
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        welcomeText = findViewById(R.id.welcomeText);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        cardDiseaseInfo = findViewById(R.id.cardDiseaseInfo);
        cardModelInfo = findViewById(R.id.cardModelInfo);
        cardAnalysisHistory = findViewById(R.id.cardAnalysisHistory);
        cardConsultationRequests = findViewById(R.id.cardConsultationRequests);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Detection UI
        expertImageView = findViewById(R.id.expertImageView);
        btnExpertCamera = findViewById(R.id.btnExpertCamera);
        btnExpertGallery = findViewById(R.id.btnExpertGallery);
        btnExpertAnalyze = findViewById(R.id.btnExpertAnalyze);
        btnExpertClear = findViewById(R.id.btnExpertClear);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.expert_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && getSupportActionBar() != null) {
            String email = currentUser.getEmail();
            if (email != null) {
                getSupportActionBar().setSubtitle(email);
                welcomeText.setText("Welcome, " + email.split("@")[0]);
            }
        }
    }

    private void setupClickListeners() {
        cardDiseaseInfo.setOnClickListener(v -> {
            Intent intent = new Intent(ExpertDashboardActivity.this, DiseaseInfoManagementActivity.class);
            startActivity(intent);
        });

        cardModelInfo.setOnClickListener(v -> {
            showModelInfo();
        });

        cardAnalysisHistory.setOnClickListener(v -> {
            showAnalysisHistory();
        });

        cardConsultationRequests.setOnClickListener(v -> {
            showConsultationRequests();
        });

        // Detection buttons
        if (btnExpertCamera != null) {
            btnExpertCamera.setOnClickListener(v -> {
                if (checkCameraPermission()) {
                    openCamera();
                } else {
                    requestCameraPermission();
                }
            });
        }

        if (btnExpertGallery != null) {
            btnExpertGallery.setOnClickListener(v -> {
                if (checkStoragePermission()) {
                    openGallery();
                } else {
                    requestStoragePermission();
                }
            });
        }

        if (btnExpertAnalyze != null) {
            btnExpertAnalyze.setOnClickListener(v -> {
                if (selectedImage != null) {
                    analyzeImage();
                } else {
                    Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnExpertClear != null) {
            btnExpertClear.setOnClickListener(v -> clearImage());
        }

        // Bottom Navigation
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                // Already on home
                return true;
            } else if (itemId == R.id.nav_disease_info) {
                Intent intent = new Intent(ExpertDashboardActivity.this, DiseaseInfoManagementActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_history) {
                showAnalysisHistory();
                return true;
            } else if (itemId == R.id.nav_consultations) {
                showConsultationRequests();
                return true;
            } else if (itemId == R.id.nav_profile) {
                showProfile();
                return true;
            }
            return false;
        });
    }

    private void initializeActivityLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        selectedImage = (Bitmap) extras.get("data");
                        expertImageView.setImageBitmap(selectedImage);
                        btnExpertAnalyze.setEnabled(true);
                        btnExpertClear.setEnabled(true);
                        btnExpertClear.setVisibility(View.VISIBLE);
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            selectedImage = BitmapFactory.decodeStream(inputStream);
                            expertImageView.setImageBitmap(selectedImage);
                            btnExpertAnalyze.setEnabled(true);
                            btnExpertClear.setEnabled(true);
                            btnExpertClear.setVisibility(View.VISIBLE);
                        } catch (FileNotFoundException e) {
                            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void analyzeImage() {
        btnExpertAnalyze.setEnabled(false);
        btnExpertAnalyze.setText("Detecting...");

        new Thread(() -> {
            List<Detection> detections = detectionService.detectDiseases(selectedImage);

            runOnUiThread(() -> {
                btnExpertAnalyze.setEnabled(true);
                btnExpertAnalyze.setText("Analyze Image");

                ImageHolder.getInstance().setImage(selectedImage);
                DetectionHolder.getInstance().setDetections(new ArrayList<>(detections));

                // Save to history for logged-in experts
                saveDetectionsToHistory(detections);

                List<Detection> highConfidenceDetections = filterHighConfidenceDetections(detections);

                Intent intent = new Intent(ExpertDashboardActivity.this, ResultActivity.class);
                intent.putExtra("detection_count", highConfidenceDetections.size());

                String confidenceWarning = "";
                if (!highConfidenceDetections.isEmpty()) {
                    StringBuilder summary = new StringBuilder();
                    for (int i = 0; i < highConfidenceDetections.size(); i++) {
                        Detection det = highConfidenceDetections.get(i);
                        summary.append(String.format("%d. %s (%.1f%%)\n",
                                i + 1, det.getClassName(), det.getConfidence() * 100));
                    }
                    intent.putExtra("detections_summary", summary.toString());
                }

                if (highConfidenceDetections.isEmpty()) {
                    intent.putExtra("disease_name", "No high-confidence detections");
                    intent.putExtra("description", getString(R.string.no_high_confidence));
                    intent.putExtra("confidence", "N/A");
                    intent.putExtra("severity_color", android.graphics.Color.GRAY);
                } else if (highConfidenceDetections.size() == 1) {
                    Detection top = highConfidenceDetections.get(0);
                    intent.putExtra("disease_name", top.getClassName());
                    intent.putExtra("description", getString(R.string.single_detection_desc));
                    intent.putExtra("confidence", String.format(Locale.getDefault(), "%.1f%%", top.getConfidence() * 100));
                    intent.putExtra("severity_color", android.graphics.Color.RED);
                } else {
                    intent.putExtra("disease_name", "Multiple Detections");
                    intent.putExtra("description", getString(R.string.multiple_detections_desc));
                    intent.putExtra("confidence", String.format(Locale.getDefault(), "%d detections", highConfidenceDetections.size()));
                    intent.putExtra("severity_color", android.graphics.Color.RED);
                }

                intent.putExtra("management", "");
                intent.putExtra("prevention", "");
                intent.putExtra("is_error", false);
                intent.putExtra("error_message", "");

                startActivity(intent);
            });
        }).start();
    }

    private void saveDetectionsToHistory(List<Detection> detections) {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null || detections == null || detections.isEmpty()) {
            return;
        }

        StringBuilder summary = new StringBuilder();
        float highestConfidence = 0f;

        for (Detection det : detections) {
            float confidence = det.getConfidence();
            if (confidence > highestConfidence) {
                highestConfidence = confidence;
            }

            String indicator = "";
            if (confidence < 0.60f) {
                indicator = " ⚠️VERY LOW";
            } else if (confidence < 0.75f) {
                indicator = " ⚠️";
            }

            summary.append(det.getClassName()).append(" (")
                   .append(String.format("%.1f%%", confidence * 100))
                   .append(indicator)
                   .append("), ");
        }
        String detectionSummary = summary.length() > 0 ?
                summary.substring(0, summary.length() - 2) : "No detections";

        String confidenceLevel;
        if (highestConfidence >= 0.85f) {
            confidenceLevel = "Very High Confidence";
        } else if (highestConfidence >= 0.75f) {
            confidenceLevel = "High Confidence";
        } else if (highestConfidence >= 0.60f) {
            confidenceLevel = "Moderate Confidence ⚠️";
        } else {
            confidenceLevel = "Low Confidence ⚠️";
        }

        String userEmail = currentUser.getEmail();
        String userName = userEmail != null ? userEmail.split("@")[0] : "Expert";

        String historyId = UUID.randomUUID().toString();
        AnalysisHistory history = new AnalysisHistory(
                historyId,
                currentUser.getUid(),
                userEmail,
                userName,
                detections.size() + " disease(s) - " + confidenceLevel,
                detectionSummary,
                System.currentTimeMillis()
        );

        db.collection("analysis_history")
                .document(historyId)
                .set(history)
                .addOnSuccessListener(aVoid -> { })
                .addOnFailureListener(e -> { });
    }

    private List<Detection> filterHighConfidenceDetections(List<Detection> detections) {
        List<Detection> result = new ArrayList<>();
        if (detections == null) return result;
        for (Detection detection : detections) {
            if (detection != null && detection.getConfidence() >= 0.75f) {
                result.add(detection);
            }
        }
        return result;
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES},
                STORAGE_PERMISSION_REQUEST);
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(cameraIntent);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(galleryIntent);
    }

    private void clearImage() {
        selectedImage = null;
        if (expertImageView != null) {
            expertImageView.setImageResource(R.drawable.image_svg);
            expertImageView.clearDetections();
        }
        if (btnExpertAnalyze != null) btnExpertAnalyze.setEnabled(false);
        if (btnExpertClear != null) {
            btnExpertClear.setEnabled(false);
            btnExpertClear.setVisibility(View.GONE);
        }
        Toast.makeText(this, "Image cleared", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                }
                break;
            case STORAGE_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery();
                } else {
                    Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void showModelInfo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("YOLOv8 Model Information")
                .setMessage("Current Detection Model Details:\n\n" +
                        "Model: YOLOv8s\n" +
                        "Framework: PyTorch\n" +
                        "Input Size: 640x640\n" +
                        "Classes: 5 Banana Diseases\n\n" +
                        "Detected Classes:\n" +
                        "1. Black Sigatoka\n" +
                        "2. Banana Bract Mosaic Disease\n" +
                        "3. Bacterial Wilt\n" +
                        "4. Fusarium Wilt\n" +
                        "5. Xanthomonas Wilt\n\n" +
                        "Model Performance:\n" +
                        "• Average Precision: 85%+\n" +
                        "• Inference Speed: ~200ms\n" +
                        "• Confidence Threshold: 75%\n\n" +
                        "Last Updated: Current Version\n" +
                        "Model Format: ONNX (Optimized)")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showProfile() {
        Intent intent = new Intent(ExpertDashboardActivity.this, ProfileActivity.class);
        startActivity(intent);
    }

    private void showAnalysisHistory() {
        Intent intent = new Intent(ExpertDashboardActivity.this, AnalysisHistoryActivity.class);
        intent.putExtra("EXPERT_MODE", true);
        startActivity(intent);
    }

    private void showConsultationRequests() {
        Intent intent = new Intent(ExpertDashboardActivity.this, ConsultationRequestsActivity.class);
        startActivity(intent);
    }

    private void loadPendingRequestsCount() {
        badgeListenerRegistration = db.collection("consultation_requests")
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null) {
                        tvNotificationBadge.setVisibility(View.GONE);
                        return;
                    }

                    int pendingCount = queryDocumentSnapshots.size();
                    if (pendingCount > 0) {
                        tvNotificationBadge.setText(String.valueOf(pendingCount));
                        tvNotificationBadge.setVisibility(View.VISIBLE);
                    } else {
                        tvNotificationBadge.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh pending count when returning to dashboard
        loadPendingRequestsCount();
    }

    private void logout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Clear SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.apply();

                    // Sign out from Firebase
                    mAuth.signOut();

                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ExpertDashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach Firestore listener to prevent permission errors on logout
        if (badgeListenerRegistration != null) {
            badgeListenerRegistration.remove();
        }
        if (detectionService != null) {
            detectionService.close();
        }
    }
}
