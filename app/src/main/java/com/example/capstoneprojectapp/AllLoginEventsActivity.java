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
import java.util.List;
import java.util.Locale;

public class AllLoginEventsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private RecyclerView recyclerView;
    private MaterialButton btnLoadMore;
    private View emptyView, loadingView;

    private FirebaseFirestore db;
    private List<java.util.Map<String, Object>> items = new ArrayList<>();
    private LoginEventsAdapter adapter;
    private DocumentSnapshot lastVisible;
    private static final int PAGE_SIZE = 25;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_login_events);

        db = FirebaseFirestore.getInstance();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("All Logins");
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
        loadingView = findViewById(R.id.loadingView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LoginEventsAdapter();
        recyclerView.setAdapter(adapter);

        btnLoadMore.setOnClickListener(v -> loadPage());

        loadPage();
    }

    private void loadPage() {
        if (isLoading) return;
        isLoading = true;
        showLoading(true);
        Query q = db.collection("login_events")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);
        if (lastVisible != null) q = q.startAfter(lastVisible);
        q.get().addOnSuccessListener(snap -> {
            List<DocumentSnapshot> docs = snap.getDocuments();
            if (!docs.isEmpty()) {
                lastVisible = docs.get(docs.size() - 1);
                for (DocumentSnapshot d : docs) {
                    java.util.Map<String, Object> m = d.getData();
                    if (m != null) items.add(m);
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
        loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private class LoginEventsAdapter extends RecyclerView.Adapter<LoginEventsAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_login_event, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            java.util.Map<String, Object> evt = items.get(pos);
            String email = (String) evt.get("email");
            String role = (String) evt.get("role");
            Long ts = (Long) evt.get("timestamp");
            h.tvTitle.setText(email != null ? email : "Unknown");
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new java.util.Date(ts != null ? ts : 0));
            h.tvSubtitle.setText((role != null ? role : "") + "  •  " + time);
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubtitle; VH(@NonNull View v){ super(v); tvTitle=v.findViewById(R.id.tvTitle); tvSubtitle=v.findViewById(R.id.tvSubtitle);} }
    }
}

