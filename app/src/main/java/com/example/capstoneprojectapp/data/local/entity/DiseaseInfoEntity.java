package com.example.capstoneprojectapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "disease_info")
public class DiseaseInfoEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String name; // diseaseName
    public String scientificName;
    public String causedBy;
    public String symptoms;
    public String treatment;
    public String prevention;
    public long updatedAt;
}

