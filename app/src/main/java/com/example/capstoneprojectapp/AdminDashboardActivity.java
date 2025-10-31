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

public class AdminDashboardActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView welcomeText;
    private MaterialCardView cardDiseaseInfo, cardModelInfo, cardAnalysisHistory, cardConsultationRequests, cardUserManagement, cardUpdateModel;
    private BottomNavigationView bottomNavigation;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";

    // Detection UI and logic
    private DetectionImageView adminImageView;
    private MaterialButton btnCamera, btnGallery, btnAnalyze, btnClear;
    private Bitmap selectedImage;
    private YOLOv8DetectionService detectionService;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String> modelPickerLauncher;
    private static final int CAMERA_PERMISSION_REQUEST = 1101;
    private static final int STORAGE_PERMISSION_REQUEST = 1102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        initializeActivityLaunchers();
        setupClickListeners();
        updateUserInfo();

        detectionService = new YOLOv8DetectionService();
        detectionService.initialize(this);
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        welcomeText = findViewById(R.id.welcomeText);
        cardDiseaseInfo = findViewById(R.id.cardDiseaseInfo);
        cardModelInfo = findViewById(R.id.cardModelInfo);
        cardAnalysisHistory = findViewById(R.id.cardAnalysisHistory);
        cardConsultationRequests = findViewById(R.id.cardConsultationRequests);
        cardUserManagement = findViewById(R.id.cardUserManagement);
        cardUpdateModel = findViewById(R.id.cardUpdateModel);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        adminImageView = findViewById(R.id.adminImageView);
        btnCamera = findViewById(R.id.btnAdminCamera);
        btnGallery = findViewById(R.id.btnAdminGallery);
        btnAnalyze = findViewById(R.id.btnAdminAnalyze);
        btnClear = findViewById(R.id.btnAdminClear);
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
        cardDiseaseInfo.setOnClickListener(v -> startActivity(new Intent(this, DiseaseInfoManagementActivity.class)));
        cardModelInfo.setOnClickListener(v -> showModelInfo());
        cardAnalysisHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, AnalysisHistoryActivity.class);
            intent.putExtra("EXPERT_MODE", true);
            startActivity(intent);
        });
        cardConsultationRequests.setOnClickListener(v -> startActivity(new Intent(this, ConsultationRequestsActivity.class)));
        cardUserManagement.setOnClickListener(v -> startActivity(new Intent(this, AdminUsersActivity.class)));
        cardUpdateModel.setOnClickListener(v -> pickAndInstallModel());

        // Detection
        btnCamera.setOnClickListener(v -> {
            if (checkCameraPermission()) openCamera(); else requestCameraPermission();
        });
        btnGallery.setOnClickListener(v -> {
            if (checkStoragePermission()) openGallery(); else requestStoragePermission();
        });
        btnAnalyze.setOnClickListener(v -> {
            if (selectedImage != null) analyzeImage(); else Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
        });
        btnClear.setOnClickListener(v -> clearImage());

        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) return true;
            if (itemId == R.id.nav_disease_info) { startActivity(new Intent(this, DiseaseInfoManagementActivity.class)); return true; }
            if (itemId == R.id.nav_profile) { startActivity(new Intent(this, ProfileActivity.class)); return true; }
            return false;
        });
    }

    private void initializeActivityLaunchers() {
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Bundle extras = result.getData().getExtras();
                selectedImage = (Bitmap) extras.get("data");
                adminImageView.setImageBitmap(selectedImage);
                btnAnalyze.setEnabled(true);
                btnClear.setEnabled(true);
                btnClear.setVisibility(View.VISIBLE);
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    selectedImage = BitmapFactory.decodeStream(inputStream);
                    adminImageView.setImageBitmap(selectedImage);
                    btnAnalyze.setEnabled(true);
                    btnClear.setEnabled(true);
                    btnClear.setVisibility(View.VISIBLE);
                } catch (FileNotFoundException e) {
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                }
            }
        });

        modelPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    boolean ok = detectionService.installCustomModel(is);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(ok ? "Model Updated" : "Update Failed")
                            .setMessage(ok ? "Custom YOLO model installed. It will be used for future analyses." : "Could not install the selected model.")
                            .setPositiveButton("OK", null)
                            .show();
                } catch (Exception e) {
                    Toast.makeText(this, "Error installing model", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void pickAndInstallModel() {
        // Let admin pick a .ptl file
        modelPickerLauncher.launch("application/*");
    }

    private void analyzeImage() {
        btnAnalyze.setEnabled(false);
        btnAnalyze.setText("Detecting...");
        new Thread(() -> {
            List<Detection> detections = detectionService.detectDiseases(selectedImage);
            runOnUiThread(() -> {
                btnAnalyze.setEnabled(true);
                btnAnalyze.setText("Analyze Image");
                ImageHolder.getInstance().setImage(selectedImage);
                DetectionHolder.getInstance().setDetections(new ArrayList<>(detections));
                // Save to history for admin user
                saveDetectionsToHistory(detections);
                Intent intent = new Intent(this, ResultActivity.class);
                intent.putExtra("detection_count", detections.size());
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
        float highest = 0f;
        for (Detection d : detections) {
            float c = d.getConfidence();
            if (c > highest) highest = c;
            String badge = c < 0.60f ? " \u26A0\uFE0FVERY LOW" : (c < 0.75f ? " \u26A0\uFE0F" : "");
            summary.append(d.getClassName())
                   .append(" (")
                   .append(String.format(java.util.Locale.getDefault(), "%.1f%%", c * 100))
                   .append(badge)
                   .append("), ");
        }
        String detectionSummary = summary.length() > 0 ? summary.substring(0, summary.length() - 2) : "No detections";

        String level;
        if (highest >= 0.85f) level = "Very High Confidence";
        else if (highest >= 0.75f) level = "High Confidence";
        else if (highest >= 0.60f) level = "Moderate Confidence \u26A0\uFE0F";
        else level = "Low Confidence \u26A0\uFE0F";

        String email = currentUser.getEmail();
        String name = email != null ? email.split("@")[0] : "Admin";
        String historyId = java.util.UUID.randomUUID().toString();

        AnalysisHistory history = new AnalysisHistory(
                historyId,
                currentUser.getUid(),
                email,
                name,
                detections.size() + " disease(s) - " + level,
                detectionSummary,
                System.currentTimeMillis()
        );

        db.collection("analysis_history").document(historyId).set(history);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }
    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES}, STORAGE_PERMISSION_REQUEST);
    }
    private void openCamera() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) cameraLauncher.launch(cameraIntent);
    }
    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(galleryIntent);
    }
    private void clearImage() {
        selectedImage = null;
        adminImageView.setImageResource(R.drawable.image_svg);
        adminImageView.clearDetections();
        btnAnalyze.setEnabled(false);
        btnClear.setEnabled(false);
        btnClear.setVisibility(View.GONE);
        Toast.makeText(this, "Image cleared", Toast.LENGTH_SHORT).show();
    }

    private void showModelInfo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("YOLOv8 Model Information")
                .setMessage("Manage and update the detection model. You can import a new .ptl model using the 'Update Model' card.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void logout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.apply();
                    mAuth.signOut();
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, LoginActivity.class);
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
        if (detectionService != null) detectionService.close();
    }
}
