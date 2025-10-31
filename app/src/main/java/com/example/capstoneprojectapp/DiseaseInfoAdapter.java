package com.example.capstoneprojectapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class DiseaseInfoAdapter extends RecyclerView.Adapter<DiseaseInfoAdapter.ViewHolder> {

    private List<DiseaseInfo> diseaseList;
    private OnDiseaseActionListener listener;
    private boolean readOnly = false;

    public interface OnDiseaseActionListener {
        void onEdit(DiseaseInfo diseaseInfo);
        void onDelete(DiseaseInfo diseaseInfo);
    }

    public DiseaseInfoAdapter(OnDiseaseActionListener listener) {
        this.diseaseList = new ArrayList<>();
        this.listener = listener;
    }

    public void setDiseaseList(List<DiseaseInfo> diseaseList) {
        this.diseaseList = diseaseList;
        notifyDataSetChanged();
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_disease_info, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiseaseInfo disease = diseaseList.get(position);
        holder.bind(disease);
    }

    @Override
    public int getItemCount() {
        return diseaseList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDiseaseName, tvCausedBy, tvSymptoms;
        MaterialButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDiseaseName = itemView.findViewById(R.id.tvDiseaseName);
            tvCausedBy = itemView.findViewById(R.id.tvCausedBy);
            tvSymptoms = itemView.findViewById(R.id.tvSymptoms);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(DiseaseInfo disease) {
            tvDiseaseName.setText(disease.getDiseaseName());
            tvCausedBy.setText("Caused by: " + disease.getCausedBy());
            tvSymptoms.setText("Symptoms: " + disease.getSymptoms());
            if (readOnly) {
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
            } else {
                btnEdit.setVisibility(View.VISIBLE);
                btnDelete.setVisibility(View.VISIBLE);
                btnEdit.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEdit(disease);
                    }
                });
                btnDelete.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDelete(disease);
                    }
                });
            }
        }
    }
}
