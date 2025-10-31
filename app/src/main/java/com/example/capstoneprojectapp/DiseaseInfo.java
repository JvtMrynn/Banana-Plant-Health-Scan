package com.example.capstoneprojectapp;

public class DiseaseInfo {
    private String id;
    private String diseaseName;
    private String scientificName;
    private String causedBy;
    private String symptoms;
    private String treatment;
    private String prevention;
    private String expertId;
    private String expertName;
    private long createdAt;
    private long updatedAt;

    // Default constructor required for Firestore
    public DiseaseInfo() {
    }

    public DiseaseInfo(String id, String diseaseName, String scientificName, String causedBy, String symptoms,
                       String treatment, String prevention, String expertId, String expertName) {
        this.id = id;
        this.diseaseName = diseaseName;
        this.scientificName = scientificName;
        this.causedBy = causedBy;
        this.symptoms = symptoms;
        this.treatment = treatment;
        this.prevention = prevention;
        this.expertId = expertId;
        this.expertName = expertName;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public String getScientificName() { return scientificName; }

    public void setScientificName(String scientificName) { this.scientificName = scientificName; }

    public String getCausedBy() {
        return causedBy;
    }

    public void setCausedBy(String causedBy) {
        this.causedBy = causedBy;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getPrevention() {
        return prevention;
    }

    public void setPrevention(String prevention) {
        this.prevention = prevention;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getExpertName() {
        return expertName;
    }

    public void setExpertName(String expertName) {
        this.expertName = expertName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
