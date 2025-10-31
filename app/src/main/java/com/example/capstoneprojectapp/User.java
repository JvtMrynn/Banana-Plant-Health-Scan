package com.example.capstoneprojectapp;

public class User {
    public static final String ROLE_FARMER = "FARMER";
    public static final String ROLE_EXPERT = "EXPERT";
    public static final String ROLE_ADMIN = "ADMIN";
    
    private String id;           // Firebase Auth UID
    private String userId;       // Custom user identifier
    private String email;
    private String name;
    private String role;         // User role: FARMER, EXPERT, or ADMIN
    private long createdAt;
    private String status;       // Registration status: PENDING or APPROVED

    // Default constructor required for Firestore
    public User() {
    }

    public User(String id, String userId, String email, String name, String role) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
        this.status = "APPROVED";
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
