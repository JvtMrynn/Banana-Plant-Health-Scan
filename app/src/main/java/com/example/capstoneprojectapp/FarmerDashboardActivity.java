package com.example.capstoneprojectapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
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
    private static final String KEY_SYNC_AFTER_LOGIN = "syncAfterLogin";
    private static final String KEY_LAST_MUNICIPALITY = "lastMunicipality";
    private static final String KEY_LAST_BARANGAY = "lastBarangay";
    private String selectedMunicipality;
    private String selectedBarangay;
    private String municipalityPlaceholder;
    private String barangayPlaceholder;

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

        // If launched in offline mode, show a brief banner
        boolean offlineMode = getIntent().getBooleanExtra("OFFLINE_MODE", false)
                || !com.example.capstoneprojectapp.util.NetworkUtils.isOnline(this);
        if (offlineMode) {
            // Show offline mode as a banner instead of Snackbar
            android.view.View banner = findViewById(R.id.seededBanner);
            com.google.android.material.textview.MaterialTextView txt = findViewById(R.id.bannerText);
            com.google.android.material.button.MaterialButton dismiss = findViewById(R.id.bannerDismiss);
            if (banner != null && txt != null && dismiss != null) {
                txt.setText("Offline mode");
                dismiss.setOnClickListener(v -> banner.setVisibility(View.GONE));
                banner.setVisibility(View.VISIBLE);
            // Append (Offline) to toolbar title
            if (getSupportActionBar() != null) {
                CharSequence currentTitle = getSupportActionBar().getTitle();
                String t = currentTitle != null ? currentTitle.toString() : "";
                if (!t.toLowerCase(java.util.Locale.getDefault()).contains("offline")) {
                    getSupportActionBar().setTitle((t.isEmpty() ? "Dashboard" : t) + " (Offline)");
                }
            }
            }
            if (bottomNavigation != null) {
                android.view.Menu menu = bottomNavigation.getMenu();
                android.view.MenuItem contact = menu.findItem(R.id.nav_contact_expert);
                android.view.MenuItem profile = menu.findItem(R.id.nav_profile);
                if (contact != null) contact.setVisible(false);
                if (profile != null) profile.setVisible(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If cache just finished seeding, inform the user that offline is ready
        try {
            android.content.SharedPreferences sp = getApplicationContext()
                    .getSharedPreferences("OfflineCachePrefs", MODE_PRIVATE);
            boolean seeded = sp.getBoolean("diseaseCacheSeeded", false);
            boolean shown = sp.getBoolean("seededToastShown", false);
            if (seeded && !shown) {
                android.view.View banner = findViewById(R.id.seededBanner);
                com.google.android.material.textview.MaterialTextView txt = findViewById(R.id.bannerText);
                com.google.android.material.button.MaterialButton dismiss = findViewById(R.id.bannerDismiss);
                if (banner != null && txt != null && dismiss != null) {
                    txt.setText("Disease info cached. Available offline.");
                    dismiss.setOnClickListener(v -> banner.setVisibility(View.GONE));
                    banner.setVisibility(View.VISIBLE);
                }
                sp.edit().putBoolean("seededToastShown", true).apply();
            }
        } catch (Exception ignored) { }
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            boolean syncFlag = sharedPreferences.getBoolean(KEY_SYNC_AFTER_LOGIN, false);
            if (syncFlag) {
                new com.example.capstoneprojectapp.data.repo.DataRepository(this)
                        .syncUnsyncedLocalHistory(currentUser);
                sharedPreferences.edit().putBoolean(KEY_SYNC_AFTER_LOGIN, false).apply();
                Toast.makeText(this, "Synced your offline analyses", Toast.LENGTH_SHORT).show();
            }
        } else {
            com.example.capstoneprojectapp.data.repo.DataRepository repo = new com.example.capstoneprojectapp.data.repo.DataRepository(this);
            try {
                int unsynced = repo.getUnsyncedCount();
                if (unsynced > 0 && com.example.capstoneprojectapp.util.NetworkUtils.isOnline(this)) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Save your analyses?")
                            .setMessage("You have " + unsynced + " recent analyses. Sign in to save them to your history?")
                            .setPositiveButton("Save and Sign In", (d, w) -> {
                                sharedPreferences.edit().putBoolean(KEY_SYNC_AFTER_LOGIN, true).apply();
                                startActivity(new Intent(this, LoginActivity.class));
                            })
                            .setNegativeButton("Not now", null)
                            .show();
                }
            } catch (Exception ignored) { }
        }
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
        } else if (itemId == R.id.action_about) {
            showAbout();
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
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null && !currentUser.isAnonymous()) {
                    confirmLocationThenAnalyze();
                } else {
                    analyzeImage();
                }
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
            } else if (itemId == R.id.nav_reports) {
                showReports();
                return false;
            } else if (itemId == R.id.nav_profile) {
                showProfile();
                return false;
            }
            return false;
        });
    }

    private void confirmLocationThenAnalyze() {
        municipalityPlaceholder = getString(R.string.municipality_placeholder);
        barangayPlaceholder = getString(R.string.barangay_placeholder);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_location_confirm, null);
        MaterialAutoCompleteTextView municipalityDropdown = dialogView.findViewById(R.id.municipalityDropdown);
        TextInputLayout barangayInputLayout = dialogView.findViewById(R.id.barangayInputLayout);
        MaterialAutoCompleteTextView barangayDropdown = dialogView.findViewById(R.id.barangayDropdown);

        String[] municipalities = getResources().getStringArray(R.array.municipality_options);
        ArrayAdapter<String> municipalityAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, municipalities);
        municipalityDropdown.setAdapter(municipalityAdapter);

        String savedMunicipality = sharedPreferences.getString(KEY_LAST_MUNICIPALITY, selectedMunicipality);
        String validMunicipality = isValidMunicipalitySelection(savedMunicipality) ? savedMunicipality : null;
        selectedMunicipality = validMunicipality;
        if (validMunicipality != null) {
            municipalityDropdown.setText(validMunicipality, false);
        } else {
            municipalityDropdown.setText(municipalityPlaceholder, false);
        }

        setupBarangayAdapter(validMunicipality, barangayInputLayout, barangayDropdown);

        String savedBarangay = sharedPreferences.getString(KEY_LAST_BARANGAY, selectedBarangay);
        String validBarangay = isValidBarangaySelection(validMunicipality, savedBarangay) ? savedBarangay : null;
        selectedBarangay = validBarangay;
        if (validBarangay != null) {
            barangayDropdown.setText(validBarangay, false);
        } else {
            barangayDropdown.setText(barangayPlaceholder, false);
        }

        municipalityDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selection = municipalities[position];
            if (selection.equals(municipalityPlaceholder)) {
                selectedMunicipality = null;
            } else {
                selectedMunicipality = selection;
            }
            selectedBarangay = null;
            setupBarangayAdapter(selectedMunicipality, barangayInputLayout, barangayDropdown);
            barangayDropdown.setText(barangayPlaceholder, false);
        });

        barangayDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String[] barangays = getBarangayOptions(selectedMunicipality);
            if (barangays == null || position >= barangays.length) {
                return;
            }
            String selection = barangays[position];
            if (selection.equals(barangayPlaceholder)) {
                selectedBarangay = null;
            } else {
                selectedBarangay = selection;
            }
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm location")
                .setView(dialogView)
                .setPositiveButton("Analyze", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button analyzeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            analyzeButton.setOnClickListener(v -> {
                if (!isLocationSelected()) {
                    Toast.makeText(this, "Please select your municipality and barangay", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedMunicipality = municipalityDropdown.getText() != null
                        ? municipalityDropdown.getText().toString().trim()
                        : selectedMunicipality;
                selectedBarangay = barangayDropdown.getText() != null
                        ? barangayDropdown.getText().toString().trim()
                        : selectedBarangay;
                if (!isLocationSelected()) {
                    Toast.makeText(this, "Please select your municipality and barangay", Toast.LENGTH_SHORT).show();
                    return;
                }
                sharedPreferences.edit()
                        .putString(KEY_LAST_MUNICIPALITY, selectedMunicipality)
                        .putString(KEY_LAST_BARANGAY, selectedBarangay)
                        .apply();
                dialog.dismiss();
                analyzeImage();
            });
        });

        dialog.show();
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
                    .setTitle("Contact Agriculture Expert")
                    .setMessage("To contact an expert, you need to register for an account.\n\n" +
                            "Benefits of registering:\n" +
                            "- Direct expert consultation\n" +
                            "- Save analysis history\n" +
                            "- Track your consultations\n" +
                            "- Get personalized advice")
                    .setPositiveButton("Register", (dialog, which) -> {
                        Intent intent = new Intent(FarmerDashboardActivity.this, RegistrationActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("Later", null)
                    .show();
            return;
        }

        Intent intent = new Intent(FarmerDashboardActivity.this, ConsultationRequestActivity.class);
        startActivity(intent);
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
                        "• Expert consultation\n\n" +
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
        if (detections.isEmpty()) return;
        // Guest: save locally for offline mode
        if (currentUser == null || currentUser.isAnonymous()) {
            StringBuilder summary = new StringBuilder();
            float highestConfidence = 0f;
            for (Detection det : detections) {
                float conf = det.getConfidence();
                if (conf > highestConfidence) highestConfidence = conf;
                summary.append(det.getClassName()).append(" (")
                        .append(String.format("%.1f%%", conf * 100))
                        .append("), ");
            }
            String detectionSummary = summary.length() > 0 ? summary.substring(0, summary.length() - 2) : "No detections";
            String confidenceLevel = highestConfidence >= 0.85f ? "Very High Confidence" :
                    highestConfidence >= 0.75f ? "High Confidence" :
                            highestConfidence >= 0.60f ? "Moderate Confidence" : "Low Confidence";
            String title = detections.size() + " disease(s) - " + confidenceLevel;
            new com.example.capstoneprojectapp.data.repo.DataRepository(this)
                    .saveAnalysisHistoryGuest(title, detectionSummary, System.currentTimeMillis());
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
        history.setImageBase64(encodeBitmapToBase64(selectedImage));
        history.setDetections(toDetectionBoxes(detections));
        history.setLocationMunicipality(getSelectedMunicipalityOrNull());
        history.setLocationBarangay(getSelectedBarangayOrNull());
        history.setLocationName(buildLocationName());
        
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

    private String encodeBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        Bitmap scaled = scaleBitmap(bitmap, 1000);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private Bitmap scaleBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap;
        }
        float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private List<DetectionBox> toDetectionBoxes(List<Detection> detections) {
        List<DetectionBox> boxes = new ArrayList<>();
        if (detections == null) {
            return boxes;
        }
        for (Detection det : detections) {
            if (det == null || det.getBoundingBox() == null) {
                continue;
            }
            RectF bbox = det.getBoundingBox();
            boxes.add(new DetectionBox(
                    bbox.left,
                    bbox.top,
                    bbox.right,
                    bbox.bottom,
                    det.getConfidence(),
                    det.getClassId(),
                    det.getClassName()
            ));
        }
        return boxes;
    }

    private boolean isLocationSelected() {
        return selectedMunicipality != null
                && !selectedMunicipality.trim().isEmpty()
                && !selectedMunicipality.equals(municipalityPlaceholder)
                && selectedBarangay != null
                && !selectedBarangay.trim().isEmpty()
                && !selectedBarangay.equals(barangayPlaceholder);
    }

    private String getSelectedMunicipalityOrNull() {
        return selectedMunicipality != null && !selectedMunicipality.equals(municipalityPlaceholder)
                ? selectedMunicipality
                : null;
    }

    private String getSelectedBarangayOrNull() {
        return selectedBarangay != null && !selectedBarangay.equals(barangayPlaceholder)
                ? selectedBarangay
                : null;
    }

    private String buildLocationName() {
        if (!isLocationSelected()) {
            return null;
        }
        return selectedMunicipality + " - " + selectedBarangay;
    }

    private void setupBarangayAdapter(String municipality,
                                      TextInputLayout barangayInputLayout,
                                      MaterialAutoCompleteTextView barangayDropdown) {
        String[] barangays = getBarangayOptions(municipality);
        if (barangays == null) {
            barangays = new String[]{barangayPlaceholder};
        }
        ArrayAdapter<String> barangayAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, barangays);
        barangayDropdown.setAdapter(barangayAdapter);
        boolean enabled = municipality != null && !municipality.equals(municipalityPlaceholder);
        barangayInputLayout.setEnabled(enabled);
        barangayDropdown.setEnabled(enabled);
    }

    private String[] getBarangayOptions(String municipality) {
        if (municipality == null || municipality.equals(municipalityPlaceholder)) {
            return null;
        }
        int arrayId = getBarangayArrayId(municipality);
        return arrayId != 0 ? getResources().getStringArray(arrayId) : null;
    }

    private boolean isValidMunicipalitySelection(String municipality) {
        if (municipality == null || municipality.trim().isEmpty()) {
            return false;
        }
        if (municipalityPlaceholder != null && municipality.equals(municipalityPlaceholder)) {
            return false;
        }
        String[] options = getResources().getStringArray(R.array.municipality_options);
        for (String option : options) {
            if (municipality.equals(option)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidBarangaySelection(String municipality, String barangay) {
        if (barangay == null || barangay.trim().isEmpty()) {
            return false;
        }
        if (barangay.equals(barangayPlaceholder)) {
            return false;
        }
        String[] options = getBarangayOptions(municipality);
        if (options == null) {
            return false;
        }
        for (String option : options) {
            if (barangay.equals(option)) {
                return true;
            }
        }
        return false;
    }

    private int getBarangayArrayId(String municipality) {
        if (municipality == null) {
            return 0;
        }
        switch (municipality) {
            case "Anahawan":
                return R.array.barangays_anahawan;
            case "Bontoc":
                return R.array.barangays_bontoc;
            case "Hinunangan":
                return R.array.barangays_hinunangan;
            case "Hinundayan":
                return R.array.barangays_hinundayan;
            case "Libagon":
                return R.array.barangays_libagon;
            case "Liloan":
                return R.array.barangays_liloan;
            case "Limasawa":
                return R.array.barangays_limasawa;
            case "Maasin City":
                return R.array.barangays_maasin_city;
            case "Macrohon":
                return R.array.barangays_macrohon;
            case "Malitbog":
                return R.array.barangays_malitbog;
            case "Padre Burgos":
                return R.array.barangays_padre_burgos;
            case "Pintuyan":
                return R.array.barangays_pintuyan;
            case "Saint Bernard":
                return R.array.barangays_saint_bernard;
            case "San Francisco":
                return R.array.barangays_san_francisco;
            case "San Juan":
                return R.array.barangays_san_juan;
            case "San Ricardo":
                return R.array.barangays_san_ricardo;
            case "Silago":
                return R.array.barangays_silago;
            case "Sogod":
                return R.array.barangays_sogod;
            case "Tomas Oppus":
                return R.array.barangays_tomas_oppus;
            default:
                return 0;
        }
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

    private void showReports() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Reports are only available for registered users", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(FarmerDashboardActivity.this, ReportsActivity.class);
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



