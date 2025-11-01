package com.example.capstoneprojectapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "analysis_history")
public class AnalysisHistoryEntity {
    @PrimaryKey
    @NonNull
    public String localId;
    public String remoteId; // nullable
    public String userId;
    public String diseaseName;
    public String summary; // detections summary
    public long timestamp;
    public boolean synced; // whether pushed to Firestore
}

