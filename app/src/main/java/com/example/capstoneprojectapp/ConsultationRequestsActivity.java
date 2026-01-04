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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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

public class ConsultationRequestsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView requestsRecyclerView;
    private LinearLayout emptyStateLayout, loadingLayout;
    private TextView tvPendingCount, tvRespondedCount;
    private ConsultationRequestAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigation;
    private boolean isAdmin = false;
    private final SimpleDateFormat conversationDateFormat =
            new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
    private MaterialButton btnLoadMore;
    private DocumentSnapshot lastVisible;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private int pendingCount = 0;
    private int reviewedCount = 0;
    private static final int PAGE_SIZE = 20;
    private ActivityResultLauncher<Intent> responseLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consultation_requests);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupActivityLaunchers();
        determineRole();
        loadRequests();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        requestsRecyclerView = findViewById(R.id.requestsRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvRespondedCount = findViewById(R.id.tvRespondedCount);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        btnLoadMore = findViewById(R.id.btnLoadMore);
        btnLoadMore.setOnClickListener(v -> fetchNextRequestPage());
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.VISIBLE);
            bottomNavigation.setSelectedItemId(R.id.nav_consultations);
            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    Class<?> target = isAdmin ? AdminDashboardActivity.class : ExpertDashboardActivity.class;
                    startActivity(new android.content.Intent(this, target));
                    return true;
                } else if (itemId == R.id.nav_disease_info) {
                    startActivity(new android.content.Intent(this, DiseaseInfoManagementActivity.class));
                    return true;
                } else if (itemId == R.id.nav_history) {
                    startActivity(new android.content.Intent(this, AnalysisHistoryActivity.class).putExtra("EXPERT_MODE", true));
                    return true;
                } else if (itemId == R.id.nav_consultations) {
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    startActivity(new android.content.Intent(this, ProfileActivity.class));
                    return true;
                }
                return false;
            });
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new ConsultationRequestAdapter();
        requestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        requestsRecyclerView.setAdapter(adapter);
    }

    private void setupActivityLaunchers() {
        responseLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadRequests();
                    }
                }
        );
    }

    private void determineRole() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(doc -> {
                        String role = doc.getString("role");
                        isAdmin = User.ROLE_ADMIN.equals(role);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    });
        }
    }

    private void loadRequests() {
        showLoading();

        adapter.clearRequests();
        pendingCount = 0;
        reviewedCount = 0;
        updateStats(0, 0);
        lastVisible = null;
        isLastPage = false;
        fetchNextRequestPage();
    }

    private void fetchNextRequestPage() {
        if (isLoading || isLastPage) {
            return;
        }

        isLoading = true;
        setLoadMoreLoading(true);

        Query query = db.collection("consultation_requests")
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

                    List<ConsultationRequest> requestList = new ArrayList<>();
                    int pendingAdd = 0;
                    int reviewedAdd = 0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        ConsultationRequest request = document.toObject(ConsultationRequest.class);
                        requestList.add(request);

                        String status = request.getStatus();
                        if (isPendingStatus(status) || isFollowUpStatus(status)) {
                            pendingAdd++;
                        } else if (isReviewedStatus(status)) {
                            reviewedAdd++;
                        }
                    }

                    if (!requestList.isEmpty()) {
                        adapter.appendRequests(requestList);
                        pendingCount += pendingAdd;
                        reviewedCount += reviewedAdd;
                        updateStats(pendingCount, reviewedCount);
                    }

                    hideLoading();

                    if (adapter.getRequestCount() == 0) {
                        showEmptyState();
                    } else {
                        showRequestsList();
                    }

                    if (queryDocumentSnapshots.size() < PAGE_SIZE) {
                        isLastPage = true;
                    }
                    updateLoadMoreVisibility();
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    if (adapter.getRequestCount() == 0) {
                        showEmptyState();
                    }
                    Toast.makeText(this, "Error loading requests: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    setLoadMoreLoading(false);
                });
    }

    private void updateStats(int pending, int responded) {
        tvPendingCount.setText(String.valueOf(pending));
        tvRespondedCount.setText(String.valueOf(responded));
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        requestsRecyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
        btnLoadMore.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        requestsRecyclerView.setVisibility(View.GONE);
        btnLoadMore.setVisibility(View.GONE);
    }

    private void showRequestsList() {
        requestsRecyclerView.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
        updateLoadMoreVisibility();
    }

    private void updateLoadMoreVisibility() {
        if (adapter.getRequestCount() == 0 || isLastPage) {
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

    private void showRespondDialog(ConsultationRequest request) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_expert_response, null);
        
        // Populate farmer information
        TextView tvFarmerInfo = dialogView.findViewById(R.id.tvFarmerInfo);
        TextView tvLocation = dialogView.findViewById(R.id.tvLocation);
        TextView tvFarmerMessage = dialogView.findViewById(R.id.tvFarmerMessage);
        TextView tvAttachedAnalysis = dialogView.findViewById(R.id.tvAttachedAnalysis);
        ImageView ivAttachedImage = dialogView.findViewById(R.id.ivAttachedImage);
        TextView tvFarmerFollowUp = dialogView.findViewById(R.id.tvFarmerFollowUp);
        TextView tvFarmerFollowUpTime = dialogView.findViewById(R.id.tvFarmerFollowUpTime);
        TextInputEditText etResponse = dialogView.findViewById(R.id.etExpertResponse);
        
        // Set farmer details
        String farmerInfo = request.getFarmerName() + "\n" +
                           "Email: " + request.getFarmerEmail() + "\n" +
                           "Disease: " + request.getDiseaseName();
        tvFarmerInfo.setText(farmerInfo);
        String locationLabel = buildLocationLabel(request);
        if (locationLabel != null) {
            tvLocation.setText("Location: " + locationLabel);
            tvLocation.setVisibility(View.VISIBLE);
        } else {
            tvLocation.setVisibility(View.GONE);
        }
        tvFarmerMessage.setText("\"" + request.getMessage() + "\"");
        String analysisLabel = buildAnalysisLabel(request);
        if (analysisLabel != null) {
            tvAttachedAnalysis.setVisibility(View.VISIBLE);
            tvAttachedAnalysis.setText(analysisLabel);
        } else {
            tvAttachedAnalysis.setVisibility(View.GONE);
        }
        bindAttachedImage(request, ivAttachedImage);
        String followUp = request.getFarmerFollowUp();
        if (followUp != null && !followUp.trim().isEmpty()) {
            tvFarmerFollowUp.setVisibility(View.VISIBLE);
            tvFarmerFollowUp.setText("Follow-up: " + followUp.trim());
            if (request.getFarmerFollowUpAt() > 0) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                tvFarmerFollowUpTime.setVisibility(View.VISIBLE);
                tvFarmerFollowUpTime.setText("Sent " + dateFormat.format(new Date(request.getFarmerFollowUpAt())));
            } else {
                tvFarmerFollowUpTime.setVisibility(View.GONE);
            }
        } else {
            tvFarmerFollowUp.setVisibility(View.GONE);
            tvFarmerFollowUpTime.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("Respond to Consultation Request")
                .setView(dialogView)
                .setPositiveButton("Send Response", null)
                .setNegativeButton("Cancel", null)
                .create();

        dlg.setCanceledOnTouchOutside(false);
        dlg.setOnShowListener(d -> {
            android.widget.Button sendBtn = dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            sendBtn.setOnClickListener(v -> {
                String response = etResponse.getText().toString().trim();
                if (TextUtils.isEmpty(response)) {
                    Toast.makeText(this, "Please enter a response", Toast.LENGTH_SHORT).show();
                    return; // keep dialog open
                }
                sendResponse(request, response);
                dlg.dismiss();
            });
        });
        dlg.show();
    }

    private void sendResponse(ConsultationRequest request, String response) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        // Get expert name from Firestore
        db.collection("users").document(currentUser.getUid())
                .get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(document -> {
                    String expertName = document.getString("name");
                    if (expertName == null) expertName = "Expert";

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
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to send response: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error getting expert info", Toast.LENGTH_SHORT).show();
                });
    }

    // RecyclerView Adapter
    private class ConsultationRequestAdapter extends RecyclerView.Adapter<ConsultationRequestAdapter.RequestViewHolder> {

        private List<ConsultationRequest> requestList = new ArrayList<>();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

        public void setRequestList(List<ConsultationRequest> requestList) {
            this.requestList = requestList;
            notifyDataSetChanged();
        }

        public void clearRequests() {
            requestList.clear();
            notifyDataSetChanged();
        }

        public void appendRequests(List<ConsultationRequest> requests) {
            int start = requestList.size();
            requestList.addAll(requests);
            notifyItemRangeInserted(start, requests.size());
        }

        public int getRequestCount() {
            return requestList.size();
        }

        @NonNull
        @Override
        public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_consultation_request, parent, false);
            return new RequestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
            ConsultationRequest request = requestList.get(position);

            holder.tvFarmerName.setText(request.getFarmerName());
            holder.tvDiseaseName.setText("Disease: " + request.getDiseaseName());
            holder.tvDetectionSummary.setText(request.getDetectionSummary());
            String locationLabel = buildLocationLabel(request);
            if (locationLabel != null) {
                holder.tvLocation.setText("Location: " + locationLabel);
                holder.tvLocation.setVisibility(View.VISIBLE);
            } else {
                holder.tvLocation.setVisibility(View.GONE);
            }
            holder.tvMessage.setText(request.getMessage());
            holder.tvTimestamp.setText(dateFormat.format(new Date(request.getCreatedAt())));

            // Status badge
            String status = request.getStatus();
            String statusLabel = getStatusLabel(status);
            holder.tvStatus.setText(statusLabel);
            if (isFollowUpStatus(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else if (isReviewedStatus(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
            }

            List<ConsultationMessage> conversation = buildConversationMessages(request);
            if (!conversation.isEmpty()) {
                holder.conversationSection.setVisibility(View.VISIBLE);
                holder.tvConversation.setText(formatConversation(conversation));
                holder.responseSection.setVisibility(View.GONE);
                holder.followUpSection.setVisibility(View.GONE);
            } else {
                holder.conversationSection.setVisibility(View.GONE);
                showLegacyResponse(request, holder);
                showLegacyFollowUp(request, holder);
            }

            String analysisLabel = buildAnalysisLabel(request);
            if (analysisLabel != null) {
                holder.tvAttachedAnalysis.setVisibility(View.VISIBLE);
                holder.tvAttachedAnalysis.setText(analysisLabel);
            } else {
                holder.tvAttachedAnalysis.setVisibility(View.GONE);
            }
            bindAnalysisImage(holder, request);

            if (isAdmin || isReviewedStatus(status)) {
                holder.btnRespond.setVisibility(View.GONE);
                holder.btnRespond.setOnClickListener(null);
            } else {
                holder.btnRespond.setVisibility(View.VISIBLE);
                holder.btnRespond.setOnClickListener(v -> {
                    Intent intent = new Intent(ConsultationRequestsActivity.this,
                            ConsultationResponseActivity.class);
                    intent.putExtra(ConsultationResponseActivity.EXTRA_REQUEST_ID, request.getId());
                    if (responseLauncher != null) {
                        responseLauncher.launch(intent);
                    } else {
                        startActivity(intent);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return requestList.size();
        }

        class RequestViewHolder extends RecyclerView.ViewHolder {
            TextView tvFarmerName, tvStatus, tvDiseaseName, tvDetectionSummary, tvMessage, tvTimestamp, tvExpertResponse;
            TextView tvAttachedAnalysis, tvLocation, tvFollowUpMessage, tvFollowUpTime;
            TextView tvConversation;
            LinearLayout responseSection;
            LinearLayout followUpSection;
            LinearLayout conversationSection;
            MaterialButton btnRespond;
            DetectionImageView ivAnalysisImage;

            public RequestViewHolder(@NonNull View itemView) {
                super(itemView);
                tvFarmerName = itemView.findViewById(R.id.tvFarmerName);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvDiseaseName = itemView.findViewById(R.id.tvDiseaseName);
                tvDetectionSummary = itemView.findViewById(R.id.tvDetectionSummary);
                tvLocation = itemView.findViewById(R.id.tvLocation);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                tvExpertResponse = itemView.findViewById(R.id.tvExpertResponse);
                responseSection = itemView.findViewById(R.id.responseSection);
                tvAttachedAnalysis = itemView.findViewById(R.id.tvAttachedAnalysis);
                ivAnalysisImage = itemView.findViewById(R.id.ivAnalysisImage);
                followUpSection = itemView.findViewById(R.id.followUpSection);
                tvFollowUpMessage = itemView.findViewById(R.id.tvFollowUpMessage);
                tvFollowUpTime = itemView.findViewById(R.id.tvFollowUpTime);
                conversationSection = itemView.findViewById(R.id.conversationSection);
                tvConversation = itemView.findViewById(R.id.tvConversation);
                btnRespond = itemView.findViewById(R.id.btnRespond);
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
            return "Follow-up";
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

    private void showLegacyResponse(ConsultationRequest request, ConsultationRequestAdapter.RequestViewHolder holder) {
        if (request.getExpertResponse() != null && !request.getExpertResponse().trim().isEmpty()) {
            holder.responseSection.setVisibility(View.VISIBLE);
            holder.tvExpertResponse.setText(request.getExpertResponse());
        } else {
            holder.responseSection.setVisibility(View.GONE);
        }
    }

    private void showLegacyFollowUp(ConsultationRequest request, ConsultationRequestAdapter.RequestViewHolder holder) {
        String followUp = request.getFarmerFollowUp();
        if (followUp != null && !followUp.trim().isEmpty()) {
            holder.followUpSection.setVisibility(View.VISIBLE);
            holder.tvFollowUpMessage.setText(followUp);
            if (request.getFarmerFollowUpAt() > 0) {
                holder.tvFollowUpTime.setVisibility(View.VISIBLE);
                holder.tvFollowUpTime.setText("Sent " +
                        conversationDateFormat.format(new Date(request.getFarmerFollowUpAt())));
            } else {
                holder.tvFollowUpTime.setVisibility(View.GONE);
            }
        } else {
            holder.followUpSection.setVisibility(View.GONE);
        }
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
        return "Expert";
    }

    private void bindAttachedImage(ConsultationRequest request, ImageView imageView) {
        if (imageView == null) return;
        String base64 = request.getAnalysisImageBase64();
        if (base64 == null || base64.trim().isEmpty()) {
            imageView.setVisibility(View.GONE);
            return;
        }
        Bitmap bitmap = decodeBase64ToBitmap(base64);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }
    }

    private void bindAnalysisImage(ConsultationRequestAdapter.RequestViewHolder holder,
                                   ConsultationRequest request) {
        if (holder == null || holder.ivAnalysisImage == null) return;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
