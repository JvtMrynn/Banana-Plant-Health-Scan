package com.example.capstoneprojectapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class DiseaseInfoViewActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView diseaseRecyclerView;
    private LinearLayout emptyStateLayout, loadingLayout;
    private DiseaseInfoViewAdapter adapter;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_info_view);

        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        loadDiseaseInfo();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        diseaseRecyclerView = findViewById(R.id.diseaseRecyclerView);
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
        adapter = new DiseaseInfoViewAdapter();
        diseaseRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        diseaseRecyclerView.setAdapter(adapter);
    }

    private void loadDiseaseInfo() {
        showLoading();

        db.collection("disease_info")
                .orderBy("diseaseName")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DiseaseInfo> diseaseList = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        DiseaseInfo disease = document.toObject(DiseaseInfo.class);
                        diseaseList.add(disease);
                    }

                    hideLoading();
                    
                    if (diseaseList.isEmpty()) {
                        showEmptyState();
                    } else {
                        showDiseaseList();
                        adapter.setDiseaseList(diseaseList);
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    showEmptyState();
                });
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        diseaseRecyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        emptyStateLayout.setVisibility(View.VISIBLE);
        diseaseRecyclerView.setVisibility(View.GONE);
    }

    private void showDiseaseList() {
        diseaseRecyclerView.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    // RecyclerView Adapter
    private static class DiseaseInfoViewAdapter extends RecyclerView.Adapter<DiseaseInfoViewAdapter.DiseaseViewHolder> {

        private List<DiseaseInfo> diseaseList = new ArrayList<>();

        public void setDiseaseList(List<DiseaseInfo> diseaseList) {
            this.diseaseList = diseaseList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DiseaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_disease_info_view, parent, false);
            return new DiseaseViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DiseaseViewHolder holder, int position) {
            DiseaseInfo disease = diseaseList.get(position);
            
            holder.tvDiseaseName.setText(disease.getDiseaseName());
            holder.tvCausedBy.setText(disease.getCausedBy());
            holder.tvSymptoms.setText(disease.getSymptoms());
            holder.tvTreatment.setText(disease.getTreatment());
            
            // Handle prevention field (might be null in older entries)
            String prevention = disease.getPrevention();
            if (prevention != null && !prevention.isEmpty()) {
                holder.tvPrevention.setText(prevention);
            } else {
                holder.tvPrevention.setText("Consult with agricultural experts for prevention strategies");
            }
        }

        @Override
        public int getItemCount() {
            return diseaseList.size();
        }

        static class DiseaseViewHolder extends RecyclerView.ViewHolder {
            TextView tvDiseaseName, tvCausedBy, tvSymptoms, tvTreatment, tvPrevention;

            public DiseaseViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDiseaseName = itemView.findViewById(R.id.tvDiseaseName);
                tvCausedBy = itemView.findViewById(R.id.tvCausedBy);
                tvSymptoms = itemView.findViewById(R.id.tvSymptoms);
                tvTreatment = itemView.findViewById(R.id.tvTreatment);
                tvPrevention = itemView.findViewById(R.id.tvPrevention);
            }
        }
    }
}
