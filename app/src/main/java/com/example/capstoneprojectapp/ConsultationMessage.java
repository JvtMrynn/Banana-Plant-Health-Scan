package com.example.capstoneprojectapp;

public class ConsultationMessage {
    private String senderRole;
    private String senderName;
    private String message;
    private long createdAt;

    public ConsultationMessage() {
    }

    public ConsultationMessage(String senderRole, String senderName, String message, long createdAt) {
        this.senderRole = senderRole;
        this.senderName = senderName;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
