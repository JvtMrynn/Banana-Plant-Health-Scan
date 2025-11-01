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
import android.widget.ImageView;
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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FarmerDashboardActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int STORAGE_PERMISSION_REQUEST = 101;

    private MaterialToolbar toolbar;
    private DetectionImageView imageView;
    private MaterialButton btnCamera, btnGallery, btnAnalyze, btnClear;
    private MaterialCardView imageCard;
    private BottomNavigationView bottomNavigation;
    private Bitmap selectedImage;
    private YOLOv8DetectionService detectionService;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmer_dashboard);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        initializeActivityLaunchers();
        setupClickListeners();
        updateUserStatus();

        detectionService = new YOLOv8DetectionService();
        detectionService.initialize(this);
        // Check for remote model updates in background
        detectionService.checkAndUpdateModelAsync((updated, msg) -> {
            if (updated) {
                runOnUiThread(() -> Toast.makeText(this, "AI model updated", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        imageView = findViewById(R.id.imageView);
        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnClear = findViewById(R.id.btnClear);
        imageCard = findViewById(R.id.imageCard);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show history icon for logged-in users (not guests)
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            getMenuInflater().inflate(R.menu.farmer_toolbar_menu, menu);
        }
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_consultations) {
            showMyConsultations();
            return true;
        } else if (itemId == R.id.action_history) {
            showAnalysisHistory();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUserStatus() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && getSupportActionBar() != null) {
            if (currentUser.isAnonymous()) {
                getSupportActionBar().setTitle("Dashboard");
                getSupportActionBar().setSubtitle("Analyze your crops");
            } else {
                String email = currentUser.getEmail();
                if (email != null) {
                    getSupportActionBar().setTitle("Farmer Dashboard");
                    getSupportActionBar().setSubtitle(email);
                }
            }
        }
    }

    private void initializeActivityLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        selectedImage = (Bitmap) extras.get("data");
                        imageView.setImageBitmap(selectedImage);
                        btnAnalyze.setEnabled(true);
                        btnClear.setEnabled(true);
                        btnClear.setVisibility(View.VISIBLE);
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
                            imageView.setImageBitmap(selectedImage);
                            btnAnalyze.setEnabled(true);
                            btnClear.setEnabled(true);
                            btnClear.setVisibility(View.VISIBLE);
                        } catch (FileNotFoundException e) {
                            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void setupClickListeners() {
        btnCamera.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                openCamera();
            } else {
                requestCameraPermission();
            }
        });

        btnGallery.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openGallery();
            } else {
                requestStoragePermission();
            }
        });

        btnAnalyze.setOnClickListener(v -> {
            if (selectedImage != null) {
                analyzeImage();
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnClear.setOnClickListener(v -> clearImage());
        
        // Bottom Navigation
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                // Already on home
                return true;
            } else if (itemId == R.id.nav_contact_expert) {
                showContactExpert();
                return false;
            } else if (itemId == R.id.nav_info) {
                showDiseaseInfo();
                return false;
            } else if (itemId == R.id.nav_about) {
                showAbout();
                return false;
            } else if (itemId == R.id.nav_profile) {
                showProfile();
                return false;
            }
            return false;
        });
    }

    private void analyzeImage() {
        btnAnalyze.setEnabled(false);
        btnAnalyze.setText("Detecting...");

        // Run detection in background thread
        new Thread(() -> {
            List<Detection> detections = detectionService.detectDiseases(selectedImage);

            // Update UI on main thread
            runOnUiThread(() -> {
                btnAnalyze.setEnabled(true);
                btnAnalyze.setText("Analyze Image");
                
                // Store image and detections in holders to avoid Intent size limitations
                ImageHolder.getInstance().setImage(selectedImage);
                DetectionHolder.getInstance().setDetections(new ArrayList<>(detections));
                
                // Save to history for logged-in users
                saveDetectionsToHistory(detections);
                
                // Navigate to ResultActivity with the detections
                Intent intent = new Intent(FarmerDashboardActivity.this, ResultActivity.class);
                intent.putExtra("detection_count", detections.size());
                
                // Pass detection details and check confidence levels
                String confidenceWarning = "";
                if (!detections.isEmpty()) {
                    StringBuilder summary = new StringBuilder();
                    boolean hasModerateConfidence = false;
                    boolean hasLowConfidence = false;
                    
                    for (int i = 0; i < detections.size(); i++) {
                        Detection det = detections.get(i);
                        summary.append(String.format("%d. %s (%.1f%%)\n",
                            i + 1, det.getClassName(), det.getConfidence() * 100));
                        
                        // Check confidence levels
                        if (det.getConfidence() < 0.75f) {
                            hasModerateConfidence = true;
                        }
                        if (det.getConfidence() < 0.85f) {
                            hasLowConfidence = true;
                        }
                    }
                    
                    // Add warning for moderate/low confidence detections
                    if (hasModerateConfidence) {
                        confidenceWarning = "\n⚠️ DETECTION CONFIDENCE NOTICE:\n" +
                            "The detection confidence is moderate (60-75%). Please verify:\n\n" +
                            "✓ Are you photographing a BANANA PLANT LEAF?\n" +
                            "✓ Is the image clear and well-lit?\n" +
                            "✓ Are disease symptoms clearly visible?\n\n" +
                            "If this is NOT a banana plant leaf, please disregard this result.\n" +
                            "For best results, photograph banana leaves with clear disease symptoms in good lighting.\n\n";
                    }
                    
                    if (hasLowConfidence && detections.get(0).getConfidence() < 0.85f) {
                        confidenceWarning = "\n⚠️ IMPORTANT: VERIFY THIS DETECTION\n" +
                            "Detection confidence: " + String.format("%.1f%%", detections.get(0).getConfidence() * 100) + "\n\n" +
                            "This detection may not be reliable. Common causes:\n" +
                            "• NOT a banana plant (e.g., hand, wall, other plants)\n" +
                            "• Poor image quality\n" +
                            "• Insufficient lighting\n" +
                            "• No clear disease symptoms\n\n" +
                            "⚠️ If you are NOT photographing a banana plant leaf with disease,\n" +
                            "please IGNORE this result and retake the photo.\n\n";
                    }
                    
                    intent.putExtra("detections_summary", summary.toString());
                }
                intent.putExtra("disease_name", detections.isEmpty() ? "No Diseases Detected" : "Multiple Detections");
                intent.putExtra("description", detections.isEmpty() ? 
                    "The image appears healthy or no banana plant diseases were detected." : 
                    confidenceWarning + "Diseases detected in the image.");
                intent.putExtra("management", "");
                intent.putExtra("prevention", "");
                intent.putExtra("confidence", detections.isEmpty() ? "N/A" : String.format("%d detections", detections.size()));
                intent.putExtra("severity_color", detections.isEmpty() ? android.graphics.Color.GREEN : android.graphics.Color.RED);
                intent.putExtra("is_error", false);
                intent.putExtra("error_message", "");
                
                startActivity(intent);
            });
        }).start();
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


    private void logout() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean isGuest = currentUser != null && currentUser.isAnonymous();

        // Clear SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        // Sign out from Firebase
        mAuth.signOut();

        String message = isGuest ? "Guest session ended" : "Logged out successfully";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        Intent intent = new Intent(FarmerDashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void clearImage() {
        selectedImage = null;
        imageView.setImageResource(R.drawable.image_svg);
        imageView.clearDetections();
        btnAnalyze.setEnabled(false);
        btnClear.setEnabled(false);
        btnClear.setVisibility(View.GONE);
        Toast.makeText(this, "Image cleared", Toast.LENGTH_SHORT).show();
    }
    
    private void showDiseaseInfo() {
        // Launch DiseaseInfoViewActivity for better readability
        Intent intent = new Intent(FarmerDashboardActivity.this, DiseaseInfoViewActivity.class);
        startActivity(intent);
    }
    
    private void showContactExpert() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser == null || currentUser.isAnonymous()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("📞 Contact Agriculture Expert")
                    .setMessage("To contact an expert, you need to register for an account.\n\n" +
                            "Benefits of registering:\n" +
                            "✓ Direct expert consultation\n" +
                            "✓ Save analysis history\n" +
                            "✓ Track your consultations\n" +
                            "✓ Get personalized advice")
                    .setPositiveButton("Register", (dialog, which) -> {
                        Intent intent = new Intent(FarmerDashboardActivity.this, RegistrationActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("Later", null)
                    .show();
            return;
        }
        
        // Show consultation request dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_consultation_request, null);
        com.google.android.material.textfield.TextInputEditText diseaseInput = 
            dialogView.findViewById(R.id.etDiseaseName);
        com.google.android.material.textfield.TextInputEditText descriptionInput = 
            dialogView.findViewById(R.id.etDescription);
        com.google.android.material.textfield.TextInputEditText messageInput = 
            dialogView.findViewById(R.id.etMessage);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("📞 Contact Agriculture Expert")
                .setView(dialogView)
                .setPositiveButton("Send Request", (dialog, which) -> {
                    String diseaseName = diseaseInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();
                    String message = messageInput.getText().toString().trim();
                    
                    if (diseaseName.isEmpty() || message.isEmpty()) {
                        Toast.makeText(this, "Please fill in disease name and message", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    sendConsultationRequest(diseaseName, description, message);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void sendConsultationRequest(String diseaseName, String description, String message) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String requestId = UUID.randomUUID().toString();
        String userEmail = currentUser.getEmail();
        String userName = userEmail != null ? userEmail.split("@")[0] : "Farmer";
        
        ConsultationRequest request = new ConsultationRequest(
                requestId,
                currentUser.getUid(),
                userEmail,
                userName,
                diseaseName,
                description,
                message
        );
        
        db.collection("consultation_requests")
                .document(requestId)
                .set(request)
                .addOnSuccessListener(aVoid -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("✅ Request Sent")
                            .setMessage("Your consultation request has been sent to our experts.\n\n" +
                                    "An expert will review your request and respond soon.\n\n" +
                                    "You can check the status in your consultation history.")
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
    
    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("About BPDDCA")
                .setMessage("Banana Plant Health Scan\n\n" +
                        "Version: 1.0\n\n" +
                        "This application uses advanced machine learning to detect and classify banana plant diseases. " +
                        "Simply capture or select an image of a banana plant leaf, and our AI model will analyze it " +
                        "to identify potential diseases.\n\n" +
                        "Features:\n" +
                        "• Real-time disease detection\n" +
                        "• Detailed disease information\n" +
                        "• Treatment recommendations\n" +
                        "• Prevention tips\n" +
                        "• Expert consultation (coming soon)\n\n" +
                        "Developed for farmers and agricultural experts to help maintain healthy banana crops.")
                .setPositiveButton("OK", null)
                .show();
    }
    
    private void showProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser != null && currentUser.isAnonymous()) {
            // Guest user - show upgrade dialog
            StringBuilder profileInfo = new StringBuilder();
            profileInfo.append("👤 Guest User\n\n");
            profileInfo.append("You are currently using the app as a guest.\n\n");
            profileInfo.append("Guest users have full access to disease detection features.\n\n");
            profileInfo.append("Benefits of upgrading:\n");
            profileInfo.append("✓ Save analysis history\n");
            profileInfo.append("✓ Access from multiple devices\n");
            profileInfo.append("✓ Priority expert consultation\n");
            profileInfo.append("✓ Advanced features\n\n");
            profileInfo.append("Upgrade to a full account to unlock all features!");
            
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Profile & Settings")
                    .setMessage(profileInfo.toString())
                    .setPositiveButton("Upgrade Account", (dialog, which) -> upgradeGuestAccount())
                    .setNegativeButton("Later", null)
                    .setNeutralButton("Logout", (dialog, which) -> logout())
                    .show();
        } else {
            // Registered user - open ProfileActivity
            Intent intent = new Intent(FarmerDashboardActivity.this, ProfileActivity.class);
            startActivity(intent);
        }
    }
    
    private void upgradeGuestAccount() {
        Toast.makeText(this, "Please register to save your data and access all features", 
                Toast.LENGTH_LONG).show();
        
        // Navigate to registration
        Intent intent = new Intent(FarmerDashboardActivity.this, RegistrationActivity.class);
        startActivity(intent);
    }
    
    private void saveDetectionsToHistory(List<Detection> detections) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        // Only save for logged-in users (not guests)
        if (currentUser == null || currentUser.isAnonymous() || detections.isEmpty()) {
            return;
        }
        
        // Create summary of detections with confidence indicators
        StringBuilder summary = new StringBuilder();
        float highestConfidence = 0f;
        
        for (Detection det : detections) {
            float confidence = det.getConfidence();
            if (confidence > highestConfidence) {
                highestConfidence = confidence;
            }
            
            // Add confidence warning indicator
            String confidenceIndicator = "";
            if (confidence < 0.60f) {
                confidenceIndicator = " ⚠️VERY LOW";
            } else if (confidence < 0.75f) {
                confidenceIndicator = " ⚠️";
            }
            
            summary.append(det.getClassName()).append(" (")
                   .append(String.format("%.1f%%", confidence * 100))
                   .append(confidenceIndicator)
                   .append("), ");
        }
        String detectionSummary = summary.length() > 0 ? 
            summary.substring(0, summary.length() - 2) : "No detections";
        
        // Determine overall confidence level for the disease name field
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
        
        // Get user email and name
        String userEmail = currentUser.getEmail();
        String userName = userEmail != null ? userEmail.split("@")[0] : "User";
        
        // Create history entry with user info for expert view
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
        
        // Save to Firestore
        db.collection("analysis_history")
                .document(historyId)
                .set(history)
                .addOnSuccessListener(aVoid -> {
                    // Success - silently saved
                })
                .addOnFailureListener(e -> {
                    // Failed to save - silent failure
                });
    }
    
    private void showMyConsultations() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Consultations are only available for registered users", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(FarmerDashboardActivity.this, MyConsultationsActivity.class);
        startActivity(intent);
    }
    
    private void showAnalysisHistory() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "History is only available for registered users", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(FarmerDashboardActivity.this, AnalysisHistoryActivity.class);
        startActivity(intent);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detectionService != null) {
            detectionService.close();
        }
    }
}
