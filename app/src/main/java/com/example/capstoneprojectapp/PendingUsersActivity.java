package com.example.capstoneprojectapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingUsersActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private RecyclerView recyclerView;
    private MaterialButton btnLoadMore;
    private View emptyView;

    private FirebaseFirestore db;
    private List<User> items = new ArrayList<>();
    private PendingAdapter adapter;
    private DocumentSnapshot lastVisible;
    private static final int PAGE_SIZE = 25;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_users);

        db = FirebaseFirestore.getInstance();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Pending Users");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        bottomNavigation = findViewById(R.id.bottomNavigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_profile);
            bottomNavigation.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    startActivity(new android.content.Intent(this, AdminDashboardActivity.class));
                    return true;
                } else if (id == R.id.nav_disease_info) {
                    startActivity(new android.content.Intent(this, DiseaseInfoManagementActivity.class));
                    return true;
                } else if (id == R.id.nav_history) {
                    startActivity(new android.content.Intent(this, AnalysisHistoryActivity.class).putExtra("EXPERT_MODE", true));
                    return true;
                } else if (id == R.id.nav_consultations) {
                    startActivity(new android.content.Intent(this, ConsultationRequestsActivity.class));
                    return true;
                } else if (id == R.id.nav_profile) {
                    return true;
                }
                return false;
            });
        }

        recyclerView = findViewById(R.id.recyclerView);
        btnLoadMore = findViewById(R.id.btnLoadMore);
        emptyView = findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PendingAdapter();
        recyclerView.setAdapter(adapter);

        btnLoadMore.setOnClickListener(v -> loadPage());

        loadPage();
    }

    private void loadPage() {
        if (isLoading) return;
        isLoading = true;
        showLoading(true);
        Query q = db.collection("users")
                .whereEqualTo("status", "PENDING")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);
        if (lastVisible != null) q = q.startAfter(lastVisible);
        q.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(snap -> {
            List<DocumentSnapshot> docs = snap.getDocuments();
            if (!docs.isEmpty()) {
                lastVisible = docs.get(docs.size() - 1);
                for (DocumentSnapshot d : docs) {
                    User u = d.toObject(User.class);
                    if (u != null) items.add(u);
                }
                adapter.notifyDataSetChanged();
            }
            updateStateAfterLoad(docs.size());
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
            updateStateAfterLoad(0);
        });
    }

    private void updateStateAfterLoad(int loaded) {
        isLoading = false;
        showLoading(false);
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        btnLoadMore.setVisibility(loaded < PAGE_SIZE ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (btnLoadMore != null) {
            btnLoadMore.setEnabled(!show);
            btnLoadMore.setText(show ? "Loading..." : "Load more");
        }
    }

    private void approveUser(User u, int position) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "APPROVED");
        db.collection("users").document(u.getId())
                .update(updates)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "User approved", Toast.LENGTH_SHORT).show();
                    items.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateStateAfterLoad(PAGE_SIZE); // keep button state reasonable
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Approval failed", Toast.LENGTH_SHORT).show());
    }

    private class PendingAdapter extends RecyclerView.Adapter<PendingAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int vt) {
            View v = getLayoutInflater().inflate(R.layout.item_pending_user, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            User u = items.get(pos);
            h.tvTitle.setText(u.getName() + " (" + u.getEmail() + ")");
            h.tvSubtitle.setText("Role: " + u.getRole() + "  •  ID: " + u.getUserId());
            h.btnApprove.setOnClickListener(v -> approveUser(u, pos));
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder { TextView tvTitle, tvSubtitle; MaterialButton btnApprove; VH(@NonNull View v){ super(v); tvTitle=v.findViewById(R.id.tvTitle); tvSubtitle=v.findViewById(R.id.tvSubtitle); btnApprove=v.findViewById(R.id.btnApprove);} }
    }
}

