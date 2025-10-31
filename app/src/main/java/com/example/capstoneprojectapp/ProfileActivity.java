package com.example.capstoneprojectapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvUserName, tvUserRole, tvAnalysisCount, tvMemberSince;
    private TextInputEditText etName, etEmail, etUserId, etRole;
    private MaterialButton btnSaveChanges, btnChangePassword, btnLogout;
    private MaterialCardView cardStatistics;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";

    private String currentUserId;
    private String currentUserRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        loadUserProfile();
        setupClickListeners();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserRole = findViewById(R.id.tvUserRole);
        tvAnalysisCount = findViewById(R.id.tvAnalysisCount);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etUserId = findViewById(R.id.etUserId);
        etRole = findViewById(R.id.etRole);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnLogout = findViewById(R.id.btnLogout);
        cardStatistics = findViewById(R.id.cardStatistics);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            // Guests should not access this activity
            finish();
            return;
        }

        // Load from Firestore
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        String email = document.getString("email");
                        String userId = document.getString("userId");
                        String role = document.getString("role");
                        Long createdAt = document.getLong("createdAt");

                        currentUserId = currentUser.getUid();
                        currentUserRole = role;

                        // Update UI
                        tvUserName.setText(name != null ? name : "User");
                        tvUserRole.setText(role != null ? role : "USER");
                        etName.setText(name);
                        etEmail.setText(email);
                        etUserId.setText(userId);
                        etRole.setText(role);

                        // Show statistics for farmers
                        if (User.ROLE_FARMER.equals(role)) {
                            cardStatistics.setVisibility(View.VISIBLE);
                            loadStatistics(currentUser.getUid(), createdAt);
                        } else {
                            cardStatistics.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void loadStatistics(String userId, Long createdAt) {
        // Load analysis count
        db.collection("analysis_history")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    tvAnalysisCount.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> {
                    tvAnalysisCount.setText("0");
                });

        // Show member since year
        if (createdAt != null) {
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
            tvMemberSince.setText(yearFormat.format(new Date(createdAt)));
        } else {
            tvMemberSince.setText("2024");
        }
    }

    private void setupClickListeners() {
        btnSaveChanges.setOnClickListener(v -> saveChanges());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void saveChanges() {
        String newName = etName.getText().toString().trim();

        if (TextUtils.isEmpty(newName)) {
            etName.setError("Name is required");
            return;
        }

        // Show progress
        btnSaveChanges.setEnabled(false);
        btnSaveChanges.setText("Saving...");

        // Update Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection("users").document(currentUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    tvUserName.setText(newName);
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    btnSaveChanges.setEnabled(true);
                    btnSaveChanges.setText("Save Changes");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update profile: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    btnSaveChanges.setEnabled(true);
                    btnSaveChanges.setText("Save Changes");
                });
    }

    private void showChangePasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_consultation_request, null);
        TextInputEditText currentPasswordInput = dialogView.findViewById(R.id.etDiseaseName);
        TextInputEditText newPasswordInput = dialogView.findViewById(R.id.etDescription);
        TextInputEditText confirmPasswordInput = dialogView.findViewById(R.id.etMessage);

        currentPasswordInput.setHint("Current Password");
        newPasswordInput.setHint("New Password");
        confirmPasswordInput.setHint("Confirm New Password");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Change", (dialog, which) -> {
                    String currentPassword = currentPasswordInput.getText().toString().trim();
                    String newPassword = newPasswordInput.getText().toString().trim();
                    String confirmPassword = confirmPasswordInput.getText().toString().trim();

                    if (TextUtils.isEmpty(currentPassword) || TextUtils.isEmpty(newPassword)) {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (newPassword.length() < 6) {
                        Toast.makeText(this, "Password must be at least 6 characters", 
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newPassword.equals(confirmPassword)) {
                        Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    changePassword(currentPassword, newPassword);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword(String currentPassword, String newPassword) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        // Re-authenticate user
        com.google.firebase.auth.AuthCredential credential = 
                com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Update password
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(this, "Password changed successfully", 
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to change password: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logout() {
        // Clear SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        // Sign out from Firebase
        mAuth.signOut();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
