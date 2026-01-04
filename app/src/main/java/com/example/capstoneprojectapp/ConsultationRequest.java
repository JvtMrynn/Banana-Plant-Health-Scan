package com.example.capstoneprojectapp;

public class ConsultationRequest {
    private String id;
    private String farmerId;
    private String farmerEmail;
    private String farmerName;
    private String diseaseName;
    private String detectionSummary;
    private String message;
    private String status; // PENDING, FOLLOW_UP, REVIEWED
    private String expertId;
    private String expertName;
    private String expertResponse;
    private long createdAt;
    private long updatedAt;
    private long respondedAt;
    private String farmerFollowUp;
    private long farmerFollowUpAt;
    private String analysisHistoryId;
    private String analysisTitle;
    private String analysisSummary;
    private String analysisImagePath;
    private String analysisImageBase64;
    private long analysisTimestamp;
    private java.util.List<DetectionBox> analysisDetections;
    private String locationName;
    private String locationMunicipality;
    private String locationBarangay;
    private java.util.List<ConsultationMessage> messages;

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

    public String getFarmerFollowUp() {
        return farmerFollowUp;
    }

    public void setFarmerFollowUp(String farmerFollowUp) {
        this.farmerFollowUp = farmerFollowUp;
    }

    public long getFarmerFollowUpAt() {
        return farmerFollowUpAt;
    }

    public void setFarmerFollowUpAt(long farmerFollowUpAt) {
        this.farmerFollowUpAt = farmerFollowUpAt;
    }

    public String getAnalysisHistoryId() {
        return analysisHistoryId;
    }

    public void setAnalysisHistoryId(String analysisHistoryId) {
        this.analysisHistoryId = analysisHistoryId;
    }

    public String getAnalysisTitle() {
        return analysisTitle;
    }

    public void setAnalysisTitle(String analysisTitle) {
        this.analysisTitle = analysisTitle;
    }

    public String getAnalysisSummary() {
        return analysisSummary;
    }

    public void setAnalysisSummary(String analysisSummary) {
        this.analysisSummary = analysisSummary;
    }

    public String getAnalysisImagePath() {
        return analysisImagePath;
    }

    public void setAnalysisImagePath(String analysisImagePath) {
        this.analysisImagePath = analysisImagePath;
    }

    public String getAnalysisImageBase64() {
        return analysisImageBase64;
    }

    public void setAnalysisImageBase64(String analysisImageBase64) {
        this.analysisImageBase64 = analysisImageBase64;
    }

    public long getAnalysisTimestamp() {
        return analysisTimestamp;
    }

    public void setAnalysisTimestamp(long analysisTimestamp) {
        this.analysisTimestamp = analysisTimestamp;
    }

    public java.util.List<DetectionBox> getAnalysisDetections() {
        return analysisDetections;
    }

    public void setAnalysisDetections(java.util.List<DetectionBox> analysisDetections) {
        this.analysisDetections = analysisDetections;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getLocationMunicipality() {
        return locationMunicipality;
    }

    public void setLocationMunicipality(String locationMunicipality) {
        this.locationMunicipality = locationMunicipality;
    }

    public String getLocationBarangay() {
        return locationBarangay;
    }

    public void setLocationBarangay(String locationBarangay) {
        this.locationBarangay = locationBarangay;
    }

    public java.util.List<ConsultationMessage> getMessages() {
        return messages;
    }

    public void setMessages(java.util.List<ConsultationMessage> messages) {
        this.messages = messages;
    }
}
