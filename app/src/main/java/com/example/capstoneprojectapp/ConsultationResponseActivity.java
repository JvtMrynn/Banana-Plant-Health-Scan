package com.example.capstoneprojectapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConsultationResponseActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST_ID = "REQUEST_ID";

    private MaterialToolbar toolbar;
    private MaterialTextView tvFarmerInfo;
    private MaterialTextView tvLocation;
    private MaterialTextView tvFarmerMessage;
    private DetectionImageView ivAnalysisImage;
    private MaterialTextView tvAnalysisTitle;
    private MaterialTextView tvAnalysisSummary;
    private MaterialTextView tvAnalysisTimestamp;
    private MaterialTextView tvAnalysisStatus;
    private MaterialCardView conversationCard;
    private MaterialTextView tvConversation;
    private TextInputEditText etResponse;
    private MaterialButton btnSendResponse;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ConsultationRequest request;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
    private final SimpleDateFormat conversationDateFormat =
            new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consultation_response);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupToolbar();

        String requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Missing request details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etResponse.addTextChangedListener(new TextWatcher() {
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

        btnSendResponse.setOnClickListener(v -> submitResponse());

        loadRequest(requestId);
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        tvFarmerInfo = findViewById(R.id.tvFarmerInfo);
        tvLocation = findViewById(R.id.tvLocation);
        tvFarmerMessage = findViewById(R.id.tvFarmerMessage);
        ivAnalysisImage = findViewById(R.id.ivAnalysisImage);
        tvAnalysisTitle = findViewById(R.id.tvAnalysisTitle);
        tvAnalysisSummary = findViewById(R.id.tvAnalysisSummary);
        tvAnalysisTimestamp = findViewById(R.id.tvAnalysisTimestamp);
        tvAnalysisStatus = findViewById(R.id.tvAnalysisStatus);
        conversationCard = findViewById(R.id.conversationCard);
        tvConversation = findViewById(R.id.tvConversation);
        etResponse = findViewById(R.id.etExpertResponse);
        btnSendResponse = findViewById(R.id.btnSendResponse);
        btnSendResponse.setEnabled(false);
        ivAnalysisImage.setOnClickListener(v -> openFullscreenImage());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Respond to Consultation");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadRequest(String requestId) {
        db.collection("consultation_requests")
                .document(requestId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    request = snapshot.toObject(ConsultationRequest.class);
                    if (request == null) {
                        Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    bindRequest(request);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load request: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void bindRequest(@NonNull ConsultationRequest request) {
        String farmerName = request.getFarmerName() != null ? request.getFarmerName() : "Farmer";
        String farmerEmail = request.getFarmerEmail() != null ? request.getFarmerEmail() : "N/A";
        String diseaseName = request.getDiseaseName() != null ? request.getDiseaseName() : "N/A";
        String farmerInfo = farmerName + "\nEmail: " + farmerEmail + "\nDisease: " + diseaseName;
        tvFarmerInfo.setText(farmerInfo);

        String locationLabel = buildLocationLabel(request);
        if (locationLabel != null) {
            tvLocation.setText("Location: " + locationLabel);
            tvLocation.setVisibility(View.VISIBLE);
        } else {
            tvLocation.setVisibility(View.GONE);
        }

        String message = request.getMessage() != null ? request.getMessage() : "";
        if (message.trim().isEmpty()) {
            tvFarmerMessage.setText("No message provided.");
        } else {
            tvFarmerMessage.setText("\"" + message.trim() + "\"");
        }

        String analysisTitle = request.getAnalysisTitle();
        if (analysisTitle == null || analysisTitle.trim().isEmpty()) {
            analysisTitle = request.getDiseaseName();
        }
        if (analysisTitle == null || analysisTitle.trim().isEmpty()) {
            analysisTitle = "Analysis result";
        }
        tvAnalysisTitle.setText(analysisTitle);

        String summary = request.getAnalysisSummary();
        if (summary == null || summary.trim().isEmpty()) {
            summary = request.getDetectionSummary();
        }
        tvAnalysisSummary.setText(summary != null ? summary : "No summary available");

        long timestamp = request.getAnalysisTimestamp() > 0
                ? request.getAnalysisTimestamp()
                : request.getCreatedAt();
        tvAnalysisTimestamp.setText(timestamp > 0 ? dateFormat.format(new Date(timestamp)) : "");

        bindAnalysisImage(request);

        List<ConsultationMessage> conversation = buildConversationMessages(request);
        if (!conversation.isEmpty()) {
            conversationCard.setVisibility(View.VISIBLE);
            tvConversation.setText(formatConversation(conversation));
        } else {
            conversationCard.setVisibility(View.GONE);
        }

        updateSendState();
    }

    private void bindAnalysisImage(ConsultationRequest request) {
        String base64 = request.getAnalysisImageBase64();
        if (base64 == null || base64.trim().isEmpty()) {
            ivAnalysisImage.setImageResource(R.drawable.image_svg);
            ivAnalysisImage.clearDetections();
            tvAnalysisStatus.setText("No image attached");
            return;
        }
        Bitmap bitmap = decodeBase64ToBitmap(base64);
        if (bitmap == null) {
            ivAnalysisImage.setImageResource(R.drawable.image_svg);
            ivAnalysisImage.clearDetections();
            tvAnalysisStatus.setText("Image data is invalid");
            return;
        }
        ivAnalysisImage.setImageBitmap(bitmap);
        List<DetectionBox> boxes = request.getAnalysisDetections();
        if (boxes != null && !boxes.isEmpty()) {
            ivAnalysisImage.setDetections(toDetections(boxes));
        } else {
            ivAnalysisImage.clearDetections();
        }
        tvAnalysisStatus.setText("Image attached");
    }

    private void openFullscreenImage() {
        if (request == null) {
            Toast.makeText(this, "Image not available", Toast.LENGTH_SHORT).show();
            return;
        }
        String base64 = request.getAnalysisImageBase64();
        if (base64 == null || base64.trim().isEmpty()) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = decodeBase64ToBitmap(base64);
        if (bitmap == null) {
            Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
            return;
        }
        ImageHolder.getInstance().setImage(bitmap);
        List<DetectionBox> boxes = request.getAnalysisDetections();
        if (boxes != null && !boxes.isEmpty()) {
            List<Detection> detections = toDetections(boxes);
            DetectionHolder.getInstance().setDetections(new ArrayList<>(detections));
        } else {
            DetectionHolder.getInstance().clearDetections();
        }
        startActivity(new Intent(this, FullscreenImageActivity.class));
    }

    private void submitResponse() {
        if (request == null) {
            Toast.makeText(this, "Request not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isReviewedStatus(request.getStatus())) {
            Toast.makeText(this, "This request is already reviewed", Toast.LENGTH_SHORT).show();
            return;
        }
        String response = etResponse.getText() != null ? etResponse.getText().toString().trim() : "";
        if (TextUtils.isEmpty(response)) {
            Toast.makeText(this, "Please enter a response", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Missing account info", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendResponse.setEnabled(false);
        btnSendResponse.setText("Sending...");

        db.collection("users").document(currentUser.getUid())
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(document -> {
                    String expertName = document.getString("name");
                    if (expertName == null || expertName.trim().isEmpty()) {
                        expertName = "Expert";
                    }
                    long now = System.currentTimeMillis();
                    Map<String, Object> messageData = new HashMap<>();
                    messageData.put("senderRole", "EXPERT");
                    messageData.put("senderName", expertName);
                    messageData.put("message", response);
                    messageData.put("createdAt", now);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "REVIEWED");
                    updates.put("expertId", currentUser.getUid());
                    updates.put("expertName", expertName);
                    updates.put("expertResponse", response);
                    updates.put("respondedAt", now);
                    updates.put("updatedAt", now);
                    updates.put("messages", FieldValue.arrayUnion(messageData));

                    db.collection("consultation_requests")
                            .document(request.getId())
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Response sent successfully",
                                        Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSendResponse.setEnabled(true);
                                btnSendResponse.setText("Send Response");
                                Toast.makeText(this, "Failed to send response: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSendResponse.setEnabled(true);
                    btnSendResponse.setText("Send Response");
                    Toast.makeText(this, "Error getting expert info", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateSendState() {
        boolean hasMessage = etResponse.getText() != null
                && !TextUtils.isEmpty(etResponse.getText().toString().trim());
        boolean canSend = hasMessage && request != null && !isReviewedStatus(request.getStatus());
        btnSendResponse.setEnabled(canSend);
    }

    private boolean isReviewedStatus(String status) {
        return "REVIEWED".equals(status) || "RESOLVED".equals(status);
    }

    private String buildLocationLabel(ConsultationRequest request) {
        String locationName = request.getLocationName();
        if (locationName != null && !locationName.trim().isEmpty()) {
            return locationName.trim();
        }
        String municipality = request.getLocationMunicipality();
        String barangay = request.getLocationBarangay();
        boolean hasMunicipality = municipality != null && !municipality.trim().isEmpty();
        boolean hasBarangay = barangay != null && !barangay.trim().isEmpty();
        if (!hasMunicipality && !hasBarangay) {
            return null;
        }
        if (hasMunicipality && hasBarangay) {
            return municipality.trim() + " - " + barangay.trim();
        }
        return hasMunicipality ? municipality.trim() : barangay.trim();
    }

    private Bitmap decodeBase64ToBitmap(String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    private List<ConsultationMessage> buildConversationMessages(ConsultationRequest request) {
        List<ConsultationMessage> messages = new ArrayList<>();
        List<ConsultationMessage> stored = request.getMessages();
        if (stored != null && !stored.isEmpty()) {
            messages.addAll(stored);
        }
        addLegacyMessages(request, messages);
        removeInitialMessage(request, messages);
        Collections.sort(messages, (a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        return messages;
    }

    private void addLegacyMessages(ConsultationRequest request, List<ConsultationMessage> messages) {
        if (request.getExpertResponse() != null && !request.getExpertResponse().trim().isEmpty()) {
            addIfMissing(messages, new ConsultationMessage(
                    "EXPERT",
                    request.getExpertName(),
                    request.getExpertResponse(),
                    request.getRespondedAt()
            ));
        }
        if (request.getFarmerFollowUp() != null && !request.getFarmerFollowUp().trim().isEmpty()) {
            addIfMissing(messages, new ConsultationMessage(
                    "FARMER",
                    request.getFarmerName(),
                    request.getFarmerFollowUp(),
                    request.getFarmerFollowUpAt()
            ));
        }
    }

    private void addIfMissing(List<ConsultationMessage> messages, ConsultationMessage candidate) {
        if (candidate.getMessage() == null || candidate.getMessage().trim().isEmpty()) {
            return;
        }
        for (ConsultationMessage existing : messages) {
            if (sameMessage(existing, candidate)) {
                return;
            }
        }
        messages.add(candidate);
    }

    private boolean sameMessage(ConsultationMessage a, ConsultationMessage b) {
        if (a == null || b == null) return false;
        String aRole = a.getSenderRole();
        String bRole = b.getSenderRole();
        String aMsg = a.getMessage() != null ? a.getMessage().trim() : "";
        String bMsg = b.getMessage() != null ? b.getMessage().trim() : "";
        if (!aMsg.equals(bMsg)) return false;
        if (aRole == null ? bRole != null : !aRole.equals(bRole)) return false;
        long aTime = a.getCreatedAt();
        long bTime = b.getCreatedAt();
        return aTime == 0 || bTime == 0 || aTime == bTime;
    }

    private void removeInitialMessage(ConsultationRequest request, List<ConsultationMessage> messages) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return;
        }
        long createdAt = request.getCreatedAt();
        String initial = request.getMessage().trim();
        messages.removeIf(msg ->
                "FARMER".equals(msg.getSenderRole())
                        && msg.getMessage() != null
                        && msg.getMessage().trim().equals(initial)
                        && (msg.getCreatedAt() == 0 || msg.getCreatedAt() == createdAt));
    }

    private String formatConversation(List<ConsultationMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            ConsultationMessage msg = messages.get(i);
            builder.append(getSenderLabel(msg));
            if (msg.getCreatedAt() > 0) {
                builder.append(" - ").append(conversationDateFormat.format(new Date(msg.getCreatedAt())));
            }
            builder.append("\n");
            if (msg.getMessage() != null) {
                builder.append(msg.getMessage().trim());
            }
            if (i < messages.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    private String getSenderLabel(ConsultationMessage msg) {
        if ("FARMER".equals(msg.getSenderRole())) {
            String name = msg.getSenderName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
            return "Farmer";
        }
        String name = msg.getSenderName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return "Expert";
    }
}
