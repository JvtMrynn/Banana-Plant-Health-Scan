package com.example.capstoneprojectapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnalysisHistoryActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView historyRecyclerView;
    private LinearLayout emptyStateLayout;
    private MaterialButton btnClearHistory;
    private BottomNavigationView bottomNavigation;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private HistoryAdapter adapter;
    private List<AnalysisHistory> historyList;
    private boolean isExpertMode = false;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_history);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        historyList = new ArrayList<>();
        
        // Check if launched in expert mode
        isExpertMode = getIntent().getBooleanExtra("EXPERT_MODE", false);

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        loadHistory();
        determineRole();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Hide clear button for experts immediately
        if (isExpertMode) {
            btnClearHistory.setVisibility(View.GONE);
        }

        btnClearHistory.setOnClickListener(v -> confirmClearHistory());

        // Bottom navigation visible only for experts
        if (bottomNavigation != null) {
            if (isExpertMode) {
                bottomNavigation.setVisibility(View.VISIBLE);
                bottomNavigation.setSelectedItemId(R.id.nav_history);
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
                        return true;
                    } else if (itemId == R.id.nav_consultations) {
                        startActivity(new android.content.Intent(this, ConsultationRequestsActivity.class));
                        return true;
                    } else if (itemId == R.id.nav_profile) {
                        startActivity(new android.content.Intent(this, ProfileActivity.class));
                        return true;
                    }
                    return false;
                });
            } else {
                bottomNavigation.setVisibility(View.GONE);
            }
        }
    }

    private void determineRole() {
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        String role = doc.getString("role");
                        isAdmin = User.ROLE_ADMIN.equals(role);
                    });
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (isExpertMode) {
                getSupportActionBar().setTitle("All Users Analysis History");
            } else {
                getSupportActionBar().setTitle("My Analysis History");
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(historyList, isExpertMode);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(adapter);
    }

    private void loadHistory() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            showEmptyState();
            return;
        }

        // Show loading (only for farmers)
        if (!isExpertMode) {
            btnClearHistory.setEnabled(false);
            btnClearHistory.setText("Loading...");
        }

        // Fetch history from Firestore
        Query query;
        if (isExpertMode) {
            // Expert mode: Load all users' history
            query = db.collection("analysis_history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(100);
        } else {
            // Farmer mode: Load only current user's history
            query = db.collection("analysis_history")
                    .whereEqualTo("userId", currentUser.getUid())
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50);
        }
        
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    historyList.clear();
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        showEmptyState();
                    } else {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            AnalysisHistory history = document.toObject(AnalysisHistory.class);
                            historyList.add(history);
                        }
                        showHistoryList();
                    }
                    
                    adapter.notifyDataSetChanged();
                    
                    // Reset button state (only for farmers)
                    if (!isExpertMode) {
                        btnClearHistory.setEnabled(true);
                        btnClearHistory.setText("Clear All History");
                    }
                })
                .addOnFailureListener(e -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("FAILED_PRECONDITION")) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Index Required")
                                .setMessage("The history feature requires a database index.\n\n" +
                                        "Please check the error log for a link to create the required index in Firebase Console.\n\n" +
                                        "Error: " + errorMsg)
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        Toast.makeText(this, "Failed to load history: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                    
                    // Reset button state (only for farmers)
                    if (!isExpertMode) {
                        btnClearHistory.setEnabled(true);
                        btnClearHistory.setText("Clear All History");
                    }
                    showEmptyState();
                });
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        historyRecyclerView.setVisibility(View.GONE);
        // Always hide clear button when empty (no history to clear)
        btnClearHistory.setVisibility(View.GONE);
    }

    private void showHistoryList() {
        emptyStateLayout.setVisibility(View.GONE);
        historyRecyclerView.setVisibility(View.VISIBLE);
        // Only show clear button for farmers
        if (isExpertMode) {
            btnClearHistory.setVisibility(View.GONE);
        } else {
            btnClearHistory.setVisibility(View.VISIBLE);
        }
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear All History?")
                .setMessage("Are you sure you want to delete all your analysis history?\n\nThis action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> clearHistory())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearHistory() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        btnClearHistory.setEnabled(false);
        btnClearHistory.setText("Clearing...");

        // Delete all history for this user
        db.collection("analysis_history")
                .whereEqualTo("userId", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        document.getReference().delete();
                    }
                    
                    historyList.clear();
                    adapter.notifyDataSetChanged();
                    showEmptyState();
                    
                    Toast.makeText(this, "History cleared successfully", Toast.LENGTH_SHORT).show();
                    btnClearHistory.setEnabled(true);
                    btnClearHistory.setText("Clear All History");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to clear history", Toast.LENGTH_SHORT).show();
                    btnClearHistory.setEnabled(true);
                    btnClearHistory.setText("Clear All History");
                });
    }

    // RecyclerView Adapter
    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

        private final List<AnalysisHistory> historyList;
        private final SimpleDateFormat dateFormat;
        private final boolean isExpertMode;

        public HistoryAdapter(List<AnalysisHistory> historyList, boolean isExpertMode) {
            this.historyList = historyList;
            this.isExpertMode = isExpertMode;
            this.dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            AnalysisHistory history = historyList.get(position);

            // Show user email for expert mode
            if (isExpertMode && history.getUserEmail() != null) {
                String displayName = history.getUserName() != null ?
                        history.getUserName() + " (" + history.getUserEmail() + ")" :
                        history.getUserEmail();
                holder.diseaseNameText.setText(displayName + "\n" + history.getDiseaseName());
            } else {
                holder.diseaseNameText.setText(history.getDiseaseName());
            }

            // Full detections list stored in confidence field (legacy). Show it below in a readable way
            String detectionsSummary = history.getConfidence();
            if (detectionsSummary != null && !detectionsSummary.trim().isEmpty()) {
                holder.detectionsSummaryText.setVisibility(View.VISIBLE);
                holder.detectionsSummaryText.setText(detectionsSummary);
            } else {
                holder.detectionsSummaryText.setVisibility(View.GONE);
            }

            // Build a short badge text from the summary (e.g., highest % like "92%")
            String badgeText = extractTopConfidencePercent(detectionsSummary);
            if (badgeText == null || badgeText.isEmpty()) {
                // Fallback: try to derive a concise label from diseaseName (contains confidence label)
                badgeText = deriveLabelFromTitle(history.getDiseaseName());
            }
            holder.confidenceText.setText(badgeText != null ? badgeText : "");

            // Timestamp
            holder.timestampText.setText(dateFormat.format(new Date(history.getTimestamp())));

            // Set confidence badge color based on the concise badge text
            int badgeColor = getConfidenceColor(badgeText);
            holder.confidenceBadge.setCardBackgroundColor(badgeColor);
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        private int getConfidenceColor(String confidence) {
            if (confidence == null) return Color.parseColor("#757575");
            
            String conf = confidence.toLowerCase();
            
            // Try to extract percentage if present
            try {
                if (conf.contains("%")) {
                    String percentStr = conf.replaceAll("[^0-9.]", "");
                    float percent = Float.parseFloat(percentStr);
                    
                    if (percent >= 85) {
                        return Color.parseColor("#2E7D32"); // Dark green - Very reliable
                    } else if (percent >= 75) {
                        return Color.parseColor("#4CAF50"); // Green - Reliable
                    } else if (percent >= 60) {
                        return Color.parseColor("#FF9800"); // Orange - Moderate (warning)
                    } else if (percent >= 45) {
                        return Color.parseColor("#F44336"); // Red - Low confidence
                    } else {
                        return Color.parseColor("#B71C1C"); // Dark red - Very low
                    }
                }
            } catch (NumberFormatException e) {
                // Fall back to text-based detection
            }
            
            // Text-based fallback
            if (conf.contains("very high")) {
                return Color.parseColor("#2E7D32"); // Dark green
            } else if (conf.contains("high")) {
                return Color.parseColor("#4CAF50"); // Green
            } else if (conf.contains("medium") || conf.contains("moderate")) {
                return Color.parseColor("#FF9800"); // Orange
            } else if (conf.contains("low")) {
                return Color.parseColor("#F44336"); // Red
            }
            return Color.parseColor("#757575"); // Gray
        }

        // Extract the highest numeric percent from a summary like
        // "Black Sigatoka (92.3%), Aphids (78.4% ⚠), Healthy (65.0%)" -> "92%"
        private String extractTopConfidencePercent(String summary) {
            if (summary == null || summary.isEmpty()) return null;
            String[] parts = summary.split(",\\s*");
            float best = -1f;
            for (String p : parts) {
                String lower = p.toLowerCase(Locale.getDefault());
                int open = lower.lastIndexOf('(');
                int close = lower.lastIndexOf(')');
                if (open >= 0 && close > open) {
                    String inside = lower.substring(open + 1, close);
                    String digits = inside.replaceAll("[^0-9.]", "");
                    if (!digits.isEmpty()) {
                        try {
                            float val = Float.parseFloat(digits);
                            if (val > best) best = val;
                        } catch (NumberFormatException ignored) { }
                    }
                }
            }
            if (best < 0f) return null;
            // Round to integer for compact badge
            int rounded = Math.round(best);
            return rounded + "%";
        }

        // If diseaseName contains a phrase like "Very High Confidence", map to a concise badge label
        private String deriveLabelFromTitle(String title) {
            if (title == null) return null;
            String t = title.toLowerCase(Locale.getDefault());
            if (t.contains("very high")) return "Very High";
            if (t.contains("high")) return "High";
            if (t.contains("moderate")) return "Moderate";
            if (t.contains("low")) return "Low";
            return null;
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView diseaseNameText;
            MaterialTextView detectionsSummaryText;
            MaterialTextView confidenceText;
            MaterialTextView timestampText;
            MaterialCardView confidenceBadge;

            public HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                diseaseNameText = itemView.findViewById(R.id.diseaseNameText);
                detectionsSummaryText = itemView.findViewById(R.id.detectionsSummaryText);
                confidenceText = itemView.findViewById(R.id.confidenceText);
                timestampText = itemView.findViewById(R.id.timestampText);
                confidenceBadge = itemView.findViewById(R.id.confidenceBadge);
            }
        }
    }
}
