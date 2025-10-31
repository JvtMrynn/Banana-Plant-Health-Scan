package com.example.capstoneprojectapp;

public class ConsultationRequest {
    private String id;
    private String farmerId;
    private String farmerEmail;
    private String farmerName;
    private String diseaseName;
    private String detectionSummary;
    private String message;
    private String status; // PENDING, IN_PROGRESS, RESOLVED
    private String expertId;
    private String expertName;
    private String expertResponse;
    private long createdAt;
    private long updatedAt;
    private long respondedAt;

    // Default constructor required for Firestore
    public ConsultationRequest() {
    }

    public ConsultationRequest(String id, String farmerId, String farmerEmail, String farmerName,
                              String diseaseName, String detectionSummary, String message) {
        this.id = id;
        this.farmerId = farmerId;
        this.farmerEmail = farmerEmail;
        this.farmerName = farmerName;
        this.diseaseName = diseaseName;
        this.detectionSummary = detectionSummary;
        this.message = message;
        this.status = "PENDING";
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

    public String getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }

    public String getFarmerEmail() {
        return farmerEmail;
    }

    public void setFarmerEmail(String farmerEmail) {
        this.farmerEmail = farmerEmail;
    }

    public String getFarmerName() {
        return farmerName;
    }

    public void setFarmerName(String farmerName) {
        this.farmerName = farmerName;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public String getDetectionSummary() {
        return detectionSummary;
    }

    public void setDetectionSummary(String detectionSummary) {
        this.detectionSummary = detectionSummary;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getExpertResponse() {
        return expertResponse;
    }

    public void setExpertResponse(String expertResponse) {
        this.expertResponse = expertResponse;
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

    public long getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(long respondedAt) {
        this.respondedAt = respondedAt;
    }
}
