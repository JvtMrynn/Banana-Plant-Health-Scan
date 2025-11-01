package com.example.capstoneprojectapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminUsersActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvLogins;
    private RecyclerView rvPending;
    private View emptyLogins;
    private View emptyPending;
    private FirebaseFirestore db;
    private BottomNavigationView bottomNavigation;
    private LoginEventsAdapter loginAdapter;
    private PendingUsersAdapter pendingAdapter;
    private List<Map<String, Object>> logins = new ArrayList<>();
    private List<User> pendingUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);
        db = FirebaseFirestore.getInstance();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("User Management");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvLogins = findViewById(R.id.rvLogins);
        rvPending = findViewById(R.id.rvPending);
        emptyLogins = findViewById(R.id.emptyLogins);
        emptyPending = findViewById(R.id.emptyPending);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        rvLogins.setLayoutManager(new LinearLayoutManager(this));
        rvPending.setLayoutManager(new LinearLayoutManager(this));
        loginAdapter = new LoginEventsAdapter();
        pendingAdapter = new PendingUsersAdapter();
        rvLogins.setAdapter(loginAdapter);
        rvPending.setAdapter(pendingAdapter);

        loadRecentLogins();
        loadPendingRegistrations();

        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_profile); // Not a perfect mapping; menu used for consistent nav
            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    startActivity(new android.content.Intent(this, AdminDashboardActivity.class));
                    return true;
                } else if (itemId == R.id.nav_disease_info) {
                    startActivity(new android.content.Intent(this, DiseaseInfoManagementActivity.class));
                    return true;
                } else if (itemId == R.id.nav_history) {
                    startActivity(new android.content.Intent(this, AnalysisHistoryActivity.class).putExtra("EXPERT_MODE", true));
                    return true;
                } else if (itemId == R.id.nav_consultations) {
                    startActivity(new android.content.Intent(this, ConsultationRequestsActivity.class));
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    return true;
                }
                return false;
            });
        }

        // View all buttons
        android.view.View btnAllLogins = findViewById(R.id.btnViewAllLogins);
        if (btnAllLogins != null) {
            btnAllLogins.setOnClickListener(v -> startActivity(new android.content.Intent(this, AllLoginEventsActivity.class)));
        }
        android.view.View btnAllPending = findViewById(R.id.btnViewAllPending);
        if (btnAllPending != null) {
            btnAllPending.setOnClickListener(v -> startActivity(new android.content.Intent(this, PendingUsersActivity.class)));
        }
    }

    private void loadRecentLogins() {
        db.collection("login_events")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(snap -> {
                    logins.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Map<String, Object> m = d.getData();
                        if (m != null) logins.add(m);
                    }
                    loginAdapter.notifyDataSetChanged();
                    emptyLogins.setVisibility(logins.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Network required")
                            .setMessage("This screen requires internet.")
                            .setPositiveButton("OK", null)
                            .show();
                });
    }

    private void loadPendingRegistrations() {
        db.collection("users")
                .whereEqualTo("status", "PENDING")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(snap -> {
                    pendingUsers.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        User u = d.toObject(User.class);
                        if (u != null) pendingUsers.add(u);
                    }
                    pendingAdapter.notifyDataSetChanged();
                    emptyPending.setVisibility(pendingUsers.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load pending users", Toast.LENGTH_SHORT).show());
    }

    private class LoginEventsAdapter extends RecyclerView.Adapter<LoginEventsAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_login_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> evt = logins.get(position);
            String email = (String) evt.get("email");
            String role = (String) evt.get("role");
            Long ts = (Long) evt.get("timestamp");
            holder.tvTitle.setText(email != null ? email : "Unknown");
            holder.tvSubtitle.setText((role != null ? role : "") + "  •  " + formatTime(ts));
        }

        @Override
        public int getItemCount() { return logins.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubtitle;
            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            }
        }
    }

    private class PendingUsersAdapter extends RecyclerView.Adapter<PendingUsersAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_user, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            User u = pendingUsers.get(position);
            holder.tvTitle.setText(u.getName() + " (" + u.getEmail() + ")");
            holder.tvSubtitle.setText("Role: " + u.getRole() + "  •  ID: " + u.getUserId());
            holder.btnApprove.setOnClickListener(v -> approveUser(u));
        }

        @Override
        public int getItemCount() { return pendingUsers.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubtitle;
            MaterialButton btnApprove;
            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
                btnApprove = itemView.findViewById(R.id.btnApprove);
            }
        }
    }

    private void approveUser(User u) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "APPROVED");
        db.collection("users").document(u.getId())
                .update(updates)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "User approved", Toast.LENGTH_SHORT).show();
                    loadPendingRegistrations();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Approval failed", Toast.LENGTH_SHORT).show());
    }

    private String formatTime(Long ts) {
        if (ts == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(ts));
    }
}


