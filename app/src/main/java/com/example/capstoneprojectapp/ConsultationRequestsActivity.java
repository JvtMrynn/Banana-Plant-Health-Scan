package com.example.capstoneprojectapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private com.google.firebase.firestore.ListenerRegistration listenerRegistration;
    private BottomNavigationView bottomNavigation;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consultation_requests);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        setupToolbar();
        setupRecyclerView();
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

        listenerRegistration = db.collection("consultation_requests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        hideLoading();
                        showEmptyState();
                        Toast.makeText(this, "Error loading requests: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        hideLoading();
                        showEmptyState();
                        updateStats(0, 0);
                        return;
                    }

                    List<ConsultationRequest> requestList = new ArrayList<>();
                    int pendingCount = 0;
                    int respondedCount = 0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        ConsultationRequest request = document.toObject(ConsultationRequest.class);
                        requestList.add(request);

                        if ("PENDING".equals(request.getStatus())) {
                            pendingCount++;
                        } else if ("RESOLVED".equals(request.getStatus())) {
                            respondedCount++;
                        }
                    }

                    // Sort by createdAt in descending order (newest first)
                    requestList.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

                    hideLoading();
                    showRequestsList();
                    adapter.setRequestList(requestList);
                    updateStats(pendingCount, respondedCount);
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
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        requestsRecyclerView.setVisibility(View.GONE);
    }

    private void showRequestsList() {
        requestsRecyclerView.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    private void showRespondDialog(ConsultationRequest request) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_expert_response, null);
        
        // Populate farmer information
        TextView tvFarmerInfo = dialogView.findViewById(R.id.tvFarmerInfo);
        TextView tvFarmerMessage = dialogView.findViewById(R.id.tvFarmerMessage);
        TextInputEditText etResponse = dialogView.findViewById(R.id.etExpertResponse);
        
        // Set farmer details
        String farmerInfo = request.getFarmerName() + "\n" +
                           "Email: " + request.getFarmerEmail() + "\n" +
                           "Disease: " + request.getDiseaseName();
        tvFarmerInfo.setText(farmerInfo);
        tvFarmerMessage.setText("\"" + request.getMessage() + "\"");

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

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "RESOLVED");
                    updates.put("expertId", currentUser.getUid());
                    updates.put("expertName", expertName);
                    updates.put("expertResponse", response);
                    updates.put("respondedAt", System.currentTimeMillis());
                    updates.put("updatedAt", System.currentTimeMillis());

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
            holder.tvMessage.setText(request.getMessage());
            holder.tvTimestamp.setText(dateFormat.format(new Date(request.getCreatedAt())));

            // Status badge
            String status = request.getStatus();
            holder.tvStatus.setText(status);
            if ("PENDING".equals(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if ("RESOLVED".equals(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            // Response section
            if ("RESOLVED".equals(status) && request.getExpertResponse() != null) {
                holder.responseSection.setVisibility(View.VISIBLE);
                holder.tvExpertResponse.setText(request.getExpertResponse());
                holder.btnRespond.setVisibility(View.GONE);
            } else {
                holder.responseSection.setVisibility(View.GONE);
                if (isAdmin) {
                    holder.btnRespond.setVisibility(View.GONE);
                } else {
                    holder.btnRespond.setVisibility(View.VISIBLE);
                    holder.btnRespond.setOnClickListener(v -> showRespondDialog(request));
                }
            }
        }

        @Override
        public int getItemCount() {
            return requestList.size();
        }

        class RequestViewHolder extends RecyclerView.ViewHolder {
            TextView tvFarmerName, tvStatus, tvDiseaseName, tvDetectionSummary, tvMessage, tvTimestamp, tvExpertResponse;
            LinearLayout responseSection;
            MaterialButton btnRespond;

            public RequestViewHolder(@NonNull View itemView) {
                super(itemView);
                tvFarmerName = itemView.findViewById(R.id.tvFarmerName);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvDiseaseName = itemView.findViewById(R.id.tvDiseaseName);
                tvDetectionSummary = itemView.findViewById(R.id.tvDetectionSummary);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                tvExpertResponse = itemView.findViewById(R.id.tvExpertResponse);
                responseSection = itemView.findViewById(R.id.responseSection);
                btnRespond = itemView.findViewById(R.id.btnRespond);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach Firestore listener to prevent permission errors on logout
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}

