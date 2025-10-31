package com.example.capstoneprojectapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
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

public class MyConsultationsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView consultationsRecyclerView;
    private LinearLayout emptyStateLayout, loadingLayout;
    private MyConsultationAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private com.google.firebase.firestore.ListenerRegistration listenerRegistration;

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

        listenerRegistration = db.collection("consultation_requests")
                .whereEqualTo("farmerId", currentUser.getUid())
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        hideLoading();
                        showEmptyState();
                        Toast.makeText(this, "Error loading consultations: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        hideLoading();
                        showEmptyState();
                        return;
                    }

                    List<ConsultationRequest> consultationList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        ConsultationRequest consultation = document.toObject(ConsultationRequest.class);
                        consultationList.add(consultation);
                    }

                    // Sort by createdAt in descending order (newest first)
                    consultationList.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

                    hideLoading();
                    showConsultationsList();
                    adapter.setConsultationList(consultationList);
                });
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        consultationsRecyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        consultationsRecyclerView.setVisibility(View.GONE);
    }

    private void showConsultationsList() {
        consultationsRecyclerView.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    // RecyclerView Adapter
    private class MyConsultationAdapter extends RecyclerView.Adapter<MyConsultationAdapter.ConsultationViewHolder> {

        private List<ConsultationRequest> consultationList = new ArrayList<>();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

        public void setConsultationList(List<ConsultationRequest> consultationList) {
            this.consultationList = consultationList;
            notifyDataSetChanged();
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

            // Status badge
            String status = consultation.getStatus();
            holder.tvStatus.setText(status);
            if ("PENDING".equals(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
                holder.tvWaitingMessage.setVisibility(View.VISIBLE);
                holder.responseSection.setVisibility(View.GONE);
            } else if ("RESOLVED".equals(status)) {
                holder.tvStatus.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                holder.tvWaitingMessage.setVisibility(View.GONE);
                
                // Show expert response
                if (consultation.getExpertResponse() != null) {
                    holder.responseSection.setVisibility(View.VISIBLE);
                    holder.tvExpertResponse.setText(consultation.getExpertResponse());
                    
                    String expertName = consultation.getExpertName();
                    if (expertName != null && !expertName.isEmpty()) {
                        holder.tvExpertName.setText("By: " + expertName);
                    } else {
                        holder.tvExpertName.setText("By: Expert");
                    }
                    
                    if (consultation.getRespondedAt() > 0) {
                        holder.tvResponseTime.setText("Responded " + 
                                dateFormat.format(new Date(consultation.getRespondedAt())));
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return consultationList.size();
        }

        class ConsultationViewHolder extends RecyclerView.ViewHolder {
            TextView tvDiseaseName, tvStatus, tvMessage, tvTimestamp;
            TextView tvExpertName, tvExpertResponse, tvResponseTime, tvWaitingMessage;
            LinearLayout responseSection;

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
