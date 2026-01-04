package com.example.capstoneprojectapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Base64;
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
import com.google.firebase.firestore.DocumentSnapshot;
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
    private MaterialButton btnLoadMore;
    private BottomNavigationView bottomNavigation;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private HistoryAdapter adapter;
    private List<AnalysisHistory> historyList;
    private boolean isExpertMode = false;
    private boolean isAdmin = false;
    private DocumentSnapshot lastVisible;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private static final int PAGE_SIZE = 20;

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
        btnLoadMore = findViewById(R.id.btnLoadMore);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Hide clear button for experts immediately
        if (isExpertMode) {
            btnClearHistory.setVisibility(View.GONE);
        }

        btnClearHistory.setOnClickListener(v -> confirmClearHistory());
        btnLoadMore.setOnClickListener(v -> fetchNextHistoryPage());

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

        historyList.clear();
        adapter.notifyDataSetChanged();
        lastVisible = null;
        isLastPage = false;
        fetchNextHistoryPage();
    }

    private void fetchNextHistoryPage() {
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

        Query query;
        if (isExpertMode) {
            query = db.collection("analysis_history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
        } else {
            query = db.collection("analysis_history")
                    .whereEqualTo("userId", currentUser.getUid())
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
        }

        if (lastVisible != null) {
            query = query.startAfter(lastVisible);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int startIndex = historyList.size();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        AnalysisHistory history = document.toObject(AnalysisHistory.class);
                        historyList.add(history);
                    }
                    if (!queryDocumentSnapshots.isEmpty()) {
                        List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                        lastVisible = docs.get(docs.size() - 1);
                    }
                    if (historyList.isEmpty()) {
                        showEmptyState();
                    } else {
                        showHistoryList();
                        int addedCount = historyList.size() - startIndex;
                        if (addedCount > 0) {
                            adapter.notifyItemRangeInserted(startIndex, addedCount);
                        } else {
                            adapter.notifyDataSetChanged();
                        }
                    }
                    if (queryDocumentSnapshots.size() < PAGE_SIZE) {
                        isLastPage = true;
                    }
                    updateLoadMoreVisibility();
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
                    if (historyList.isEmpty()) {
                        showEmptyState();
                    }
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    setLoadMoreLoading(false);
                });
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        historyRecyclerView.setVisibility(View.GONE);
        // Always hide clear button when empty (no history to clear)
        btnClearHistory.setVisibility(View.GONE);
        btnLoadMore.setVisibility(View.GONE);
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
        updateLoadMoreVisibility();
    }

    private void updateLoadMoreVisibility() {
        if (historyList.isEmpty() || isLastPage) {
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

            String location = buildLocationLabel(history);
            if (location != null) {
                holder.locationText.setText("Location: " + location);
                holder.locationText.setVisibility(View.VISIBLE);
            } else {
                holder.locationText.setVisibility(View.GONE);
            }

            bindHistoryImage(holder, history);
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
            DetectionImageView historyImage;
            MaterialTextView locationText;

            public HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                diseaseNameText = itemView.findViewById(R.id.diseaseNameText);
                detectionsSummaryText = itemView.findViewById(R.id.detectionsSummaryText);
                confidenceText = itemView.findViewById(R.id.confidenceText);
                timestampText = itemView.findViewById(R.id.timestampText);
                confidenceBadge = itemView.findViewById(R.id.confidenceBadge);
                historyImage = itemView.findViewById(R.id.ivHistoryImage);
                locationText = itemView.findViewById(R.id.locationText);
            }
        }

        private void bindHistoryImage(HistoryViewHolder holder, AnalysisHistory history) {
            String base64 = history.getImageBase64();
            if (base64 == null || base64.trim().isEmpty()) {
                holder.historyImage.setVisibility(View.GONE);
                holder.historyImage.clearDetections();
                return;
            }
            Bitmap bitmap = decodeBase64ToBitmap(base64);
            if (bitmap == null) {
                holder.historyImage.setVisibility(View.GONE);
                holder.historyImage.clearDetections();
                return;
            }
            holder.historyImage.setImageBitmap(scaleBitmap(bitmap, 900));
            List<DetectionBox> boxes = history.getDetections();
            if (boxes != null && !boxes.isEmpty()) {
                holder.historyImage.setDetections(toDetections(boxes));
            } else {
                holder.historyImage.clearDetections();
            }
            holder.historyImage.setVisibility(View.VISIBLE);
        }

        private String buildLocationLabel(AnalysisHistory history) {
            if (history == null) {
                return null;
            }
            String municipality = history.getLocationMunicipality();
            String barangay = history.getLocationBarangay();
            if (municipality != null && !municipality.trim().isEmpty()
                    && barangay != null && !barangay.trim().isEmpty()) {
                return municipality.trim() + " - " + barangay.trim();
            }
            String combined = history.getLocationName();
            if (combined != null && !combined.trim().isEmpty()) {
                return combined.trim();
            }
            return null;
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
    }
}
