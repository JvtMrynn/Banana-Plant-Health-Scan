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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView welcomeText;
    private MaterialCardView cardDiseaseInfo, cardModelInfo, cardAnalysisHistory, cardReports, cardConsultationRequests, cardUserManagement, cardUpdateModel;
    private BottomNavigationView bottomNavigation;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_LAST_MUNICIPALITY = "lastMunicipality";
    private static final String KEY_LAST_BARANGAY = "lastBarangay";
    private String selectedMunicipality;
    private String selectedBarangay;
    private String municipalityPlaceholder;
    private String barangayPlaceholder;

    // Detection UI and logic
    private DetectionImageView adminImageView;
    private MaterialButton btnCamera, btnGallery, btnAnalyze, btnClear;
    private Bitmap selectedImage;
    private YOLOv8DetectionService detectionService;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
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
        cardReports = findViewById(R.id.cardReports);
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
        cardReports.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportsActivity.class);
            intent.putExtra("ADMIN_MODE", true);
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
            if (selectedImage != null) {
                confirmLocationThenAnalyze();
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
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

        // Note: modelPickerLauncher no longer used (URL-based publish instead)
    }

    private void pickAndInstallModel() {
        // Show dialog for global URL publish; keep file picker path for local install only if needed later
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_model_publish, null);
        com.google.android.material.textfield.TextInputEditText etUrl = view.findViewById(R.id.etModelUrl);
        com.google.android.material.textfield.TextInputEditText etVersion = view.findViewById(R.id.etModelVersion);
        etVersion.setText(String.valueOf(System.currentTimeMillis()));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Publish Model (URL)")
                .setView(view)
                .setPositiveButton("Publish", (d,w)->{
                    String url = etUrl.getText()!=null?etUrl.getText().toString().trim():"";
                    String verStr = etVersion.getText()!=null?etVersion.getText().toString().trim():"";
                    if (url.isEmpty()) { android.widget.Toast.makeText(this, "Enter model URL", android.widget.Toast.LENGTH_SHORT).show(); return; }
                    long version = 0L; try { version = Long.parseLong(verStr); } catch (Exception ignored) { version = System.currentTimeMillis(); }
                    java.util.Map<String,Object> meta = new java.util.HashMap<>();
                    meta.put("version", version);
                    meta.put("url", url);
                    meta.put("updatedAt", System.currentTimeMillis());
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("ml_models").document("yolov8")
                            .set(meta)
                            .addOnSuccessListener(a-> {
                                // Trigger the standard client update check instead of downloading here
                                detectionService.checkAndUpdateModelAsync((updated, msg) -> runOnUiThread(() -> {
                                    String text = updated ? "Model published and installed" : ("Published; " + msg);
                                    android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
                                }));
                            })
                            .addOnFailureListener(e-> android.widget.Toast.makeText(this, "Publish failed: "+e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        history.setImageBase64(encodeBitmapToBase64(selectedImage));
        history.setDetections(toDetectionBoxes(detections));
        history.setLocationMunicipality(getSelectedMunicipalityOrNull());
        history.setLocationBarangay(getSelectedBarangayOrNull());
        history.setLocationName(buildLocationName());

        db.collection("analysis_history").document(historyId).set(history);
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
