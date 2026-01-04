package com.example.capstoneprojectapp;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConsultationRequestActivity extends AppCompatActivity {
    public static final String EXTRA_FROM_RESULT = "FROM_RESULT";
    public static final String EXTRA_ANALYSIS_TITLE = "ANALYSIS_TITLE";
    public static final String EXTRA_ANALYSIS_SUMMARY = "ANALYSIS_SUMMARY";
    public static final String EXTRA_ANALYSIS_TIMESTAMP = "ANALYSIS_TIMESTAMP";

    private MaterialToolbar toolbar;
    private MaterialTextView tvAnalysisTitle;
    private MaterialTextView tvAnalysisSummary;
    private MaterialTextView tvAnalysisTimestamp;
    private MaterialTextView tvAnalysisStatus;
    private DetectionImageView ivAnalysisImage;
    private TextInputEditText etMessage;
    private MaterialButton btnSelectAnalysis;
    private MaterialButton btnSendRequest;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_LAST_MUNICIPALITY = "lastMunicipality";
    private static final String KEY_LAST_BARANGAY = "lastBarangay";

    private String selectedHistoryId;
    private String selectedAnalysisTitle;
    private String selectedAnalysisSummary;
    private long selectedAnalysisTimestamp;
    private String selectedImageBase64;
    private List<DetectionBox> selectedDetections;
    private String selectedLocationName;
    private String selectedLocationMunicipality;
    private String selectedLocationBarangay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consultation_request);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupToolbar();

        if (!ensureSignedIn()) {
            return;
        }
        enforceFarmerRole();

        btnSelectAnalysis.setOnClickListener(v -> showHistoryPicker());
        btnSendRequest.setOnClickListener(v -> submitRequest());
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (getIntent().getBooleanExtra(EXTRA_FROM_RESULT, false)) {
            loadFromResult();
        } else {
            loadLatestAnalysis();
        }
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        tvAnalysisTitle = findViewById(R.id.tvAnalysisTitle);
        tvAnalysisSummary = findViewById(R.id.tvAnalysisSummary);
        tvAnalysisTimestamp = findViewById(R.id.tvAnalysisTimestamp);
        tvAnalysisStatus = findViewById(R.id.tvAnalysisStatus);
        ivAnalysisImage = findViewById(R.id.ivAnalysisImage);
        etMessage = findViewById(R.id.etMessage);
        btnSelectAnalysis = findViewById(R.id.btnSelectAnalysis);
        btnSendRequest = findViewById(R.id.btnSendRequest);
        btnSendRequest.setEnabled(false);
        ivAnalysisImage.setOnClickListener(v -> openFullscreenImage());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Consultation Request");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private boolean ensureSignedIn() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Sign in required")
                    .setMessage("Please register or sign in to contact an expert.")
                    .setPositiveButton("Register", (dialog, which) -> {
                        startActivity(new android.content.Intent(this, RegistrationActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> finish())
                    .show();
            return false;
        }
        return true;
    }

    private void enforceFarmerRole() {
        new SessionManager(this).fetchRole(role -> {
            if (!User.ROLE_FARMER.equals(role)) {
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Farmers only")
                            .setMessage("Only farmer accounts can send consultation requests.")
                            .setPositiveButton("OK", (dialog, which) -> finish())
                            .show();
                });
            }
        });
    }

    private void loadFromResult() {
        selectedAnalysisTitle = getIntent().getStringExtra(EXTRA_ANALYSIS_TITLE);
        selectedAnalysisSummary = getIntent().getStringExtra(EXTRA_ANALYSIS_SUMMARY);
        selectedAnalysisTimestamp = getIntent().getLongExtra(EXTRA_ANALYSIS_TIMESTAMP, System.currentTimeMillis());
        Bitmap bitmap = ImageHolder.getInstance().getImage();
        selectedImageBase64 = encodeBitmapToBase64(bitmap);
        selectedDetections = toDetectionBoxes(DetectionHolder.getInstance().getDetections());
        loadLocationFromPreferences();
        if (selectedAnalysisTitle == null || selectedAnalysisTitle.trim().isEmpty()
                || selectedImageBase64 == null || selectedImageBase64.trim().isEmpty()) {
            loadLatestAnalysis();
        } else {
            applySelectedAnalysis();
        }
    }

    private void loadLatestAnalysis() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("analysis_history")
                .whereEqualTo("userId", currentUser.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        setEmptyAnalysisState("Run an analysis to start a consultation.");
                        return;
                    }
                    // Cast to QueryDocumentSnapshot
                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                    AnalysisHistory history = doc.toObject(AnalysisHistory.class);
                    if (history == null) {
                        setEmptyAnalysisState("Run an analysis to start a consultation.");
                        return;
                    }
                    applySelectedHistory(doc.getId(), history);
                })
                .addOnFailureListener(e -> setEmptyAnalysisState("Unable to load analysis history."));
    }

    private void showHistoryPicker() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("analysis_history")
                .whereEqualTo("userId", currentUser.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No analysis history found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<HistoryItem> historyItems = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        AnalysisHistory history = document.toObject(AnalysisHistory.class);
                        if (history == null) continue;
                        String imageBase64 = history.getImageBase64();
                        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
                            continue;
                        }
                        historyItems.add(new HistoryItem(document.getId(), history));
                    }

                    showHistoryDialog(historyItems);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showHistoryDialog(List<HistoryItem> historyItems) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_select_analysis_history, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rvHistory);
        MaterialTextView tvEmpty = dialogView.findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        if (historyItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Select analysis history")
                .setView(dialogView)
                .setNegativeButton("Close", null)
                .create();

        HistoryPickerAdapter adapter = new HistoryPickerAdapter(historyItems, item -> {
            applySelectedHistory(item.id, item.history);
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        dialog.show();
    }

    private void applySelectedHistory(String historyId, @NonNull AnalysisHistory history) {
        selectedHistoryId = historyId;
        selectedAnalysisTitle = history.getDiseaseName();
        selectedAnalysisSummary = history.getConfidence();
        selectedAnalysisTimestamp = history.getTimestamp();
        selectedImageBase64 = history.getImageBase64();
        selectedDetections = history.getDetections();
        setSelectedLocation(history.getLocationMunicipality(), history.getLocationBarangay(), history.getLocationName());
        applySelectedAnalysis();
    }

    private void applySelectedAnalysis() {
        if (selectedAnalysisTitle == null || selectedAnalysisTitle.trim().isEmpty()) {
            setEmptyAnalysisState("Select an analysis result to continue.");
            return;
        }

        tvAnalysisTitle.setText(selectedAnalysisTitle);
        tvAnalysisSummary.setText(selectedAnalysisSummary != null ? selectedAnalysisSummary : "No summary available");
        tvAnalysisTimestamp.setText(formatTimestamp(selectedAnalysisTimestamp));

        if (selectedImageBase64 != null && !selectedImageBase64.trim().isEmpty()) {
            Bitmap bitmap = decodeBase64ToBitmap(selectedImageBase64);
            if (bitmap != null) {
                ivAnalysisImage.setImageBitmap(bitmap);
                bindDetections();
                tvAnalysisStatus.setText("Image attached");
            } else {
                setImageMissingState("Image data is invalid. Re-run analysis.");
            }
        } else {
            setImageMissingState("Image is required. Select an analysis with an image.");
        }

        updateSendState();
    }

    private void setEmptyAnalysisState(String message) {
        selectedHistoryId = null;
        selectedAnalysisTitle = null;
        selectedAnalysisSummary = null;
        selectedAnalysisTimestamp = 0L;
        selectedImageBase64 = null;
        selectedDetections = null;
        selectedLocationName = null;
        selectedLocationMunicipality = null;
        selectedLocationBarangay = null;
        tvAnalysisTitle.setText("No analysis selected");
        tvAnalysisSummary.setText("Run an analysis to attach results.");
        tvAnalysisTimestamp.setText("");
        tvAnalysisStatus.setText(message);
        ivAnalysisImage.setImageResource(R.drawable.image_svg);
        ivAnalysisImage.clearDetections();
        updateSendState();
    }

    private void setImageMissingState(String message) {
        ivAnalysisImage.setImageResource(R.drawable.image_svg);
        ivAnalysisImage.clearDetections();
        tvAnalysisStatus.setText(message);
    }

    private void updateSendState() {
        boolean hasMessage = etMessage.getText() != null && !TextUtils.isEmpty(etMessage.getText().toString().trim());
        boolean hasImage = selectedImageBase64 != null && !selectedImageBase64.trim().isEmpty();
        btnSendRequest.setEnabled(hasMessage && hasImage && selectedAnalysisTitle != null);
    }

    private void submitRequest() {
        String message = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedImageBase64 == null || selectedImageBase64.trim().isEmpty()) {
            Toast.makeText(this, "Please attach an analysis image", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedAnalysisTitle == null || selectedAnalysisTitle.trim().isEmpty()) {
            Toast.makeText(this, "Select an analysis result first", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String requestId = UUID.randomUUID().toString();
        String userEmail = currentUser.getEmail();
        String userName = userEmail != null ? userEmail.split("@")[0] : "Farmer";
        long now = System.currentTimeMillis();

        ConsultationRequest request = new ConsultationRequest(
                requestId,
                currentUser.getUid(),
                userEmail,
                userName,
                selectedAnalysisTitle,
                selectedAnalysisSummary != null ? selectedAnalysisSummary : "No summary available",
                message
        );
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setAnalysisHistoryId(selectedHistoryId);
        request.setAnalysisTitle(selectedAnalysisTitle);
        request.setAnalysisSummary(selectedAnalysisSummary);
        request.setAnalysisImageBase64(selectedImageBase64);
        request.setAnalysisTimestamp(selectedAnalysisTimestamp);
        request.setAnalysisDetections(selectedDetections);
        request.setLocationName(selectedLocationName);
        request.setLocationMunicipality(selectedLocationMunicipality);
        request.setLocationBarangay(selectedLocationBarangay);
        List<ConsultationMessage> messages = new ArrayList<>();
        messages.add(new ConsultationMessage("FARMER", userName, message, now));
        request.setMessages(messages);

        btnSendRequest.setEnabled(false);
        btnSendRequest.setText("Sending...");

        db.collection("consultation_requests")
                .document(requestId)
                .set(request)
                .addOnSuccessListener(aVoid -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Request Sent")
                            .setMessage("Your consultation request has been sent to our experts.\n\n" +
                                    "You can check the status in your consultation history.")
                            .setPositiveButton("OK", (dialog, which) -> finish())
                            .show();
                })
                .addOnFailureListener(e -> {
                    btnSendRequest.setEnabled(true);
                    btnSendRequest.setText("Send Request");
                    Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "";
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        return dateFormat.format(new Date(timestamp));
    }

    private String encodeBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        Bitmap scaled = scaleBitmap(bitmap, 1000);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private Bitmap decodeBase64ToBitmap(String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    private List<Detection> toDetections(List<DetectionBox> boxes) {
        List<Detection> detections = new ArrayList<>();
        if (boxes == null) {
            return detections;
        }
        for (DetectionBox box : boxes) {
            if (box == null) {
                continue;
            }
            RectF rect = new RectF(box.getLeft(), box.getTop(), box.getRight(), box.getBottom());
            detections.add(new Detection(rect, box.getConfidence(), box.getClassId(), box.getClassName()));
        }
        return detections;
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

    private void bindDetections() {
        if (selectedDetections == null || selectedDetections.isEmpty()) {
            ivAnalysisImage.clearDetections();
            return;
        }
        ivAnalysisImage.setDetections(toDetections(selectedDetections));
    }

    private void openFullscreenImage() {
        if (selectedImageBase64 == null || selectedImageBase64.trim().isEmpty()) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = decodeBase64ToBitmap(selectedImageBase64);
        if (bitmap == null) {
            Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
            return;
        }
        ImageHolder.getInstance().setImage(bitmap);
        if (selectedDetections != null && !selectedDetections.isEmpty()) {
            List<Detection> detections = toDetections(selectedDetections);
            DetectionHolder.getInstance().setDetections(new ArrayList<>(detections));
        } else {
            DetectionHolder.getInstance().clearDetections();
        }
        startActivity(new Intent(this, FullscreenImageActivity.class));
    }

    private void loadLocationFromPreferences() {
        if (sharedPreferences == null) return;
        String municipality = sharedPreferences.getString(KEY_LAST_MUNICIPALITY, null);
        String barangay = sharedPreferences.getString(KEY_LAST_BARANGAY, null);
        setSelectedLocation(municipality, barangay, null);
    }

    private void setSelectedLocation(String municipality, String barangay, String locationName) {
        selectedLocationMunicipality = normalizeLocationPart(municipality);
        selectedLocationBarangay = normalizeLocationPart(barangay);
        if (locationName != null && !locationName.trim().isEmpty()) {
            selectedLocationName = locationName.trim();
            return;
        }
        if (selectedLocationMunicipality != null && selectedLocationBarangay != null) {
            selectedLocationName = selectedLocationMunicipality + " - " + selectedLocationBarangay;
        } else if (selectedLocationMunicipality != null) {
            selectedLocationName = selectedLocationMunicipality;
        } else if (selectedLocationBarangay != null) {
            selectedLocationName = selectedLocationBarangay;
        } else {
            selectedLocationName = null;
        }
    }

    private String normalizeLocationPart(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class HistoryItem {
        private final String id;
        private final AnalysisHistory history;

        private HistoryItem(String id, AnalysisHistory history) {
            this.id = id;
            this.history = history;
        }
    }

    private interface HistorySelectionListener {
        void onSelected(HistoryItem item);
    }

    private class HistoryPickerAdapter extends RecyclerView.Adapter<HistoryPickerAdapter.HistoryViewHolder> {
        private final List<HistoryItem> items;
        private final SimpleDateFormat dateFormat =
                new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        private final HistorySelectionListener listener;

        private HistoryPickerAdapter(List<HistoryItem> items, HistorySelectionListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_analysis_history_select, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            AnalysisHistory history = item.history;
            holder.title.setText(history.getDiseaseName());
            holder.summary.setText(history.getConfidence() != null ? history.getConfidence() : "No summary");
            holder.timestamp.setText(formatTimestamp(history.getTimestamp()));

            Bitmap bitmap = decodeBase64ToBitmap(history.getImageBase64());
            if (bitmap != null) {
                holder.thumbnail.setImageBitmap(scaleBitmap(bitmap, 200));
            } else {
                holder.thumbnail.setImageResource(R.drawable.image_svg);
            }
            holder.itemView.setOnClickListener(v -> listener.onSelected(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            MaterialTextView title;
            MaterialTextView summary;
            MaterialTextView timestamp;

            HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.ivThumbnail);
                title = itemView.findViewById(R.id.tvTitle);
                summary = itemView.findViewById(R.id.tvSummary);
                timestamp = itemView.findViewById(R.id.tvTimestamp);
            }
        }
    }
}
