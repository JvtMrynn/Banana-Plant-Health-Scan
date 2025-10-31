package com.example.capstoneprojectapp;

public class AnalysisHistory {
    private String id;
    private String userId;
    private String userEmail;
    private String userName;
    private String diseaseName;
    private String confidence;
    private long timestamp;
    private String imagePath; // Path to saved image (optional)

    // Default constructor required for Firestore
    public AnalysisHistory() {
    }

    public AnalysisHistory(String id, String userId, String diseaseName, String confidence, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.diseaseName = diseaseName;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }
    
    public AnalysisHistory(String id, String userId, String userEmail, String userName, 
                          String diseaseName, String confidence, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userName = userName;
        this.diseaseName = diseaseName;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
