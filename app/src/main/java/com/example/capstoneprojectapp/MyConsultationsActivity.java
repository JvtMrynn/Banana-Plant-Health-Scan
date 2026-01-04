package com.example.capstoneprojectapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyConsultationsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView consultationsRecyclerView;
    private LinearLayout emptyStateLayout, loadingLayout;
    private MyConsultationAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private final SimpleDateFormat conversationDateFormat =
            new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
    private MaterialButton btnLoadMore;
    private DocumentSnapshot lastVisible;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private static final int PAGE_SIZE = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_consultations);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        loadMyConsultations();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        consultationsRecyclerView = findViewById(R.id.consultationsRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        btnLoadMore = findViewById(R.id.btnLoadMore);
        btnLoadMore.setOnClickListener(v -> fetchNextConsultationsPage());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new MyConsultationAdapter();
        consultationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        consultationsRecyclerView.setAdapter(adapter);
    }

    private void loadMyConsultations() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            showEmptyState();
            return;
        }

        showLoading();

        adapter.clearConsultations();
        lastVisible = null;
        isLastPage = false;
        fetchNextConsultationsPage();
    }

    private void fetchNextConsultationsPage() {
        if (isLoading || isLastPage) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            showEmptyState();
            return;
        }

        isLoading = true;
        setLoadMoreLoading(true);

        Query query = db.collection("consultation_requests")
                .whereEqualTo("farmerId", currentUser.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                        lastVisible = docs.get(docs.size() - 1);
                    }

                    List<ConsultationRequest> consultationList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        ConsultationRequest consultation = document.toObject(ConsultationRequest.class);
                        consultationList.add(consultation);
                    }

                    if (!consultationList.isEmpty()) {
                        adapter.appendConsultations(consultationList);
                    }

                    hideLoading();

                    if (adapter.getConsultationCount() == 0) {
                        showEmptyState();
                    } else {
                        showConsultationsList();
                    }

                    if (queryDocumentSnapshots.size() < PAGE_SIZE) {
                        isLastPage = true;
                    }
                    updateLoadMoreVisibility();
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    if (adapter.getConsultationCount() == 0) {
                        showEmptyState();
                    }
                    Toast.makeText(this, "Error loading consultations: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    setLoadMoreLoading(false);
                });
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        consultationsRecyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
        btnLoadMore.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        consultationsRecyclerView.setVisibility(View.GONE);
        btnLoadMore.setVisibility(View.GONE);
    }

    private void showConsultationsList() {
        consultationsRecyclerView.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
        updateLoadMoreVisibility();
    }

    private void updateLoadMoreVisibility() {
        if (adapter.getConsultationCount() == 0 || isLastPage) {
            btnLoadMore.setVisibility(View.GONE);
        } else {
            btnLoadMore.setVisibility(View.VISIBLE);
        }
    }

    private void setLoadMoreLoading(boolean loading) {
        if (btnLoadMore == null) return;
        btnLoadMore.setEnabled(!loading);
        btnLoadMore.setText(loading ? "Loading..." : "Load more");
    }

    // RecyclerView Adapter
    private class MyConsultationAdapter extends RecyclerView.Adapter<MyConsultationAdapter.ConsultationViewHolder> {

        private List<ConsultationRequest> consultationList = new ArrayList<>();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

        public void setConsultationList(List<ConsultationRequest> consultationList) {
            this.consultationList = consultationList;
            notifyDataSetChanged();
        }

        public void clearConsultations() {
            consultationList.clear();
            notifyDataSetChanged();
        }

        public void appendConsultations(List<ConsultationRequest> consultations) {
            int start = consultationList.size();
            consultationList.addAll(consultations);
            notifyItemRangeInserted(start, consultations.size());
        }

        public int getConsultationCount() {
            return consultationList.size();
        }

        @NonNull
        @Override
        public ConsultationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_consultation, parent, false);
            return new ConsultationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ConsultationViewHolder holder, int position) {
            ConsultationRequest consultation = consultationList.get(position);

            holder.tvDiseaseName.setText(consultation.getDiseaseName());
            holder.tvMessage.setText(consultation.getMessage());
            holder.tvTimestamp.setText("Sent " + dateFormat.format(new Date(consultation.getCreatedAt())));
            String locationLabel = buildLocationLabel(consultation);
            if (locationLabel != null) {
                holder.tvLocation.setText("Location: " + locationLabel);
                holder.tvLocation.setVisibility(View.VISIBLE);
            } else {
                holder.tvLocation.setVisibility(View.GONE);
            }

            // Status badge
            String status = consultation.getStatus();
            String statusLabel = getStatusLabel(status);
            holder.tvStatus.setText(statusLabel);
            if (isFollowUpStatus(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else if (isReviewedStatus(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
            }

            if (isPendingStatus(status)) {
                holder.tvWaitingMessage.setText("Awaiting expert review...");
                holder.tvWaitingMessage.setVisibility(View.VISIBLE);
            } else if (isFollowUpStatus(status)) {
                holder.tvWaitingMessage.setText("Follow-up sent. Awaiting expert reply...");
                holder.tvWaitingMessage.setVisibility(View.VISIBLE);
            } else {
                holder.tvWaitingMessage.setVisibility(View.GONE);
            }

            // Attached analysis
            String analysisLabel = buildAnalysisLabel(consultation);
            if (analysisLabel != null) {
                holder.tvAttachedAnalysis.setText(analysisLabel);
                holder.tvAttachedAnalysis.setVisibility(View.VISIBLE);
            } else {
                holder.tvAttachedAnalysis.setVisibility(View.GONE);
            }

            bindAnalysisImage(holder, consultation);

            List<ConsultationMessage> conversation = buildConversationMessages(consultation);
            if (!conversation.isEmpty()) {
                holder.conversationSection.setVisibility(View.VISIBLE);
                holder.tvConversation.setText(formatConversation(conversation));
                holder.responseSection.setVisibility(View.GONE);
                holder.followUpSection.setVisibility(View.GONE);
            } else {
                holder.conversationSection.setVisibility(View.GONE);
                showLegacyResponse(consultation, holder);
                showLegacyFollowUp(consultation, holder);
            }

            if (isReviewedStatus(status)) {
                holder.btnFollowUp.setVisibility(View.VISIBLE);
                holder.btnFollowUp.setOnClickListener(v -> showFollowUpDialog(consultation));
            } else {
                holder.btnFollowUp.setVisibility(View.GONE);
                holder.btnFollowUp.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return consultationList.size();
        }

        class ConsultationViewHolder extends RecyclerView.ViewHolder {
            TextView tvDiseaseName, tvStatus, tvMessage, tvTimestamp;
            TextView tvExpertName, tvExpertResponse, tvResponseTime, tvWaitingMessage;
            TextView tvAttachedAnalysis, tvFollowUpMessage, tvFollowUpTime;
            TextView tvConversation;
            LinearLayout responseSection;
            LinearLayout followUpSection;
            LinearLayout conversationSection;
            MaterialButton btnFollowUp;
            DetectionImageView ivAnalysisImage;
            TextView tvLocation;

            public ConsultationViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDiseaseName = itemView.findViewById(R.id.tvDiseaseName);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                tvExpertName = itemView.findViewById(R.id.tvExpertName);
                tvExpertResponse = itemView.findViewById(R.id.tvExpertResponse);
                tvResponseTime = itemView.findViewById(R.id.tvResponseTime);
                tvWaitingMessage = itemView.findViewById(R.id.tvWaitingMessage);
                responseSection = itemView.findViewById(R.id.responseSection);
                tvAttachedAnalysis = itemView.findViewById(R.id.tvAttachedAnalysis);
                tvLocation = itemView.findViewById(R.id.tvLocation);
                followUpSection = itemView.findViewById(R.id.followUpSection);
                tvFollowUpMessage = itemView.findViewById(R.id.tvFollowUpMessage);
                tvFollowUpTime = itemView.findViewById(R.id.tvFollowUpTime);
                conversationSection = itemView.findViewById(R.id.conversationSection);
                tvConversation = itemView.findViewById(R.id.tvConversation);
                btnFollowUp = itemView.findViewById(R.id.btnFollowUp);
                ivAnalysisImage = itemView.findViewById(R.id.ivAnalysisImage);
            }
        }
    }

    private boolean isReviewedStatus(String status) {
        return "REVIEWED".equals(status) || "RESOLVED".equals(status);
    }

    private boolean isFollowUpStatus(String status) {
        return "FOLLOW_UP".equals(status);
    }

    private boolean isPendingStatus(String status) {
        return status == null || "PENDING".equals(status);
    }

    private String getStatusLabel(String status) {
        if (isReviewedStatus(status)) {
            return "Reviewed";
        }
        if (isFollowUpStatus(status)) {
            return "Awaiting expert";
        }
        return "Pending";
    }

    private String buildAnalysisLabel(ConsultationRequest request) {
        String title = request.getAnalysisTitle();
        String summary = request.getAnalysisSummary();
        boolean hasTitle = title != null && !title.trim().isEmpty();
        boolean hasSummary = summary != null && !summary.trim().isEmpty();
        if (!hasTitle && !hasSummary) {
            return null;
        }
        StringBuilder label = new StringBuilder("Attached analysis: ");
        if (hasTitle) {
            label.append(title.trim());
        }
        if (hasSummary) {
            if (hasTitle) {
                label.append(" - ");
            }
            label.append(summary.trim());
        }
        String imagePath = request.getAnalysisImagePath();
        String imageBase64 = request.getAnalysisImageBase64();
        if ((imagePath != null && !imagePath.trim().isEmpty())
                || (imageBase64 != null && !imageBase64.trim().isEmpty())) {
            label.append(" (image attached)");
        }
        return label.toString();
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

    private void showLegacyResponse(ConsultationRequest consultation, MyConsultationAdapter.ConsultationViewHolder holder) {
        if (consultation.getExpertResponse() != null && !consultation.getExpertResponse().trim().isEmpty()) {
            holder.responseSection.setVisibility(View.VISIBLE);
            holder.tvExpertResponse.setText(consultation.getExpertResponse());

            String expertName = consultation.getExpertName();
            if (expertName != null && !expertName.isEmpty()) {
                holder.tvExpertName.setText("By: " + expertName);
            } else {
                holder.tvExpertName.setText("By: Expert");
            }

            if (consultation.getRespondedAt() > 0) {
                holder.tvResponseTime.setText("Reviewed " +
                        conversationDateFormat.format(new Date(consultation.getRespondedAt())));
            } else {
                holder.tvResponseTime.setText("");
            }
        } else {
            holder.responseSection.setVisibility(View.GONE);
        }
    }

    private void showLegacyFollowUp(ConsultationRequest consultation, MyConsultationAdapter.ConsultationViewHolder holder) {
        String followUp = consultation.getFarmerFollowUp();
        if (followUp != null && !followUp.trim().isEmpty()) {
            holder.followUpSection.setVisibility(View.VISIBLE);
            holder.tvFollowUpMessage.setText(followUp);
            if (consultation.getFarmerFollowUpAt() > 0) {
                holder.tvFollowUpTime.setVisibility(View.VISIBLE);
                holder.tvFollowUpTime.setText("Sent " +
                        conversationDateFormat.format(new Date(consultation.getFarmerFollowUpAt())));
            } else {
                holder.tvFollowUpTime.setVisibility(View.GONE);
            }
        } else {
            holder.followUpSection.setVisibility(View.GONE);
        }
    }

    private void bindAnalysisImage(MyConsultationAdapter.ConsultationViewHolder holder, ConsultationRequest request) {
        String base64 = request.getAnalysisImageBase64();
        if (base64 == null || base64.trim().isEmpty()) {
            holder.ivAnalysisImage.setVisibility(View.GONE);
            holder.ivAnalysisImage.clearDetections();
            return;
        }
        Bitmap bitmap = decodeBase64ToBitmap(base64);
        if (bitmap == null) {
            holder.ivAnalysisImage.setVisibility(View.GONE);
            holder.ivAnalysisImage.clearDetections();
            return;
        }
        holder.ivAnalysisImage.setImageBitmap(scaleBitmap(bitmap, 800));
        List<DetectionBox> boxes = request.getAnalysisDetections();
        if (boxes != null && !boxes.isEmpty()) {
            holder.ivAnalysisImage.setDetections(toDetections(boxes));
        } else {
            holder.ivAnalysisImage.clearDetections();
        }
        holder.ivAnalysisImage.setVisibility(View.VISIBLE);
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
            return "You";
        }
        String name = msg.getSenderName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return "Expert";
    }

    private void showFollowUpDialog(ConsultationRequest request) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_farmer_follow_up, null);
        TextInputEditText etFollowUp = dialogView.findViewById(R.id.etFarmerFollowUp);

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("Send Follow-up")
                .setView(dialogView)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .create();

        dlg.setOnShowListener(d -> {
            android.widget.Button sendBtn = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            sendBtn.setOnClickListener(v -> {
                String followUp = etFollowUp.getText() != null ? etFollowUp.getText().toString().trim() : "";
                if (TextUtils.isEmpty(followUp)) {
                    Toast.makeText(this, "Please enter your follow-up message", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendFollowUp(request, followUp);
                dlg.dismiss();
            });
        });
        dlg.show();
    }

    private void sendFollowUp(ConsultationRequest request, String followUp) {
        if (request == null || request.getId() == null) return;
        String farmerName = request.getFarmerName();
        if (farmerName == null || farmerName.trim().isEmpty()) {
            farmerName = "Farmer";
        }
        long now = System.currentTimeMillis();
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderRole", "FARMER");
        messageData.put("senderName", farmerName);
        messageData.put("message", followUp);
        messageData.put("createdAt", now);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "FOLLOW_UP");
        updates.put("farmerFollowUp", followUp);
        updates.put("farmerFollowUpAt", now);
        updates.put("updatedAt", now);
        updates.put("messages", FieldValue.arrayUnion(messageData));

        db.collection("consultation_requests")
                .document(request.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Follow-up sent", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Failed to send follow-up: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
