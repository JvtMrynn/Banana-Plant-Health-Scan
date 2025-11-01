package com.example.capstoneprojectapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.UUID;

public class RegistrationActivity extends AppCompatActivity {

    private EditText nameEditText, usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private RadioGroup roleRadioGroup;
    private RadioButton radioFarmer, radioExpert, radioAdmin;
    private Button registerButton;
    private TextView loginTextView;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        nameEditText = findViewById(R.id.nameEditText);
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        roleRadioGroup = findViewById(R.id.roleRadioGroup);
        radioFarmer = findViewById(R.id.radioFarmer);
        radioExpert = findViewById(R.id.radioExpert);
//        radioAdmin = findViewById(R.id.radioAdmin);
        registerButton = findViewById(R.id.registerButton);
        loginTextView = findViewById(R.id.loginTextView);
        progressBar = findViewById(R.id.progressBar);

        registerButton.setOnClickListener(v -> registerUser());

        loginTextView.setOnClickListener(v -> {
            finish(); // Go back to login
        });
    }

    private void registerUser() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Name is required");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return;
        }
        // Strict email validation
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return;
        }

        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError("Username is required");
            return;
        }

        // Username rules: 3-20 chars, letters, numbers, underscore
        if (!username.matches("^[A-Za-z0-9_]{3,20}$")) {
            usernameEditText.setError("Use 3-20 letters, digits, or _ only");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return;
        }
        // Strong password: 8+ chars, 1 upper, 1 lower, 1 digit, 1 symbol
        if (!password.matches("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")) {
            passwordEditText.setError("Min 8 chars, include upper, lower, number, symbol");
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        registerButton.setEnabled(false);

        // Check username availability in login_lookup
        String key = "u_" + username.toLowerCase();
        db.collection("login_lookup").document(key).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        registerButton.setEnabled(true);
                        usernameEditText.setError("Username already taken");
                    } else {
                        // Create user with Firebase Authentication
                        mAuth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener(this, task -> {
                                    if (task.isSuccessful()) {
                                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                        if (firebaseUser != null) {
                                            // Use username as userId
                                            String userId = username;

                                            // Get selected role
                                            String selectedRole = getSelectedRole();

                                            // Create user object with selected role
                                            User user = new User(
                                                    firebaseUser.getUid(),
                                                    userId,
                                                    email,
                                                    name,
                                                    selectedRole
                                            );

                                            // New users require admin approval
                                            user.setStatus("PENDING");

                                            // Save to Firestore
                                            saveUserToFirestore(user);
                                        }
                                    } else {
                                        progressBar.setVisibility(View.GONE);
                                        registerButton.setEnabled(true);
                                        Toast.makeText(RegistrationActivity.this,
                                                "Registration failed: " + task.getException().getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    registerButton.setEnabled(true);
                    Toast.makeText(this, "Error checking username: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String getSelectedRole() {
        int selectedId = roleRadioGroup.getCheckedRadioButtonId();
        
        if (selectedId == R.id.radioFarmer) {
            return User.ROLE_FARMER;
        } else if (selectedId == R.id.radioExpert) {
            return User.ROLE_EXPERT;
        }
//        else if (selectedId == R.id.radioAdmin) {
//            return User.ROLE_ADMIN;
//        }
        
        return User.ROLE_FARMER; // Default
    }

    private void saveUserToFirestore(User user) {
        db.collection("users").document(user.getId())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    // Create public login lookup entry for username (userId)
                    try {
                        java.util.Map<String, Object> lookup = new java.util.HashMap<>();
                        lookup.put("uid", user.getId());
                        lookup.put("email", user.getEmail());
                        db.collection("login_lookup")
                                .document("u_" + user.getUserId().toLowerCase())
                                .set(lookup);
                    } catch (Exception ignored) { }

                    progressBar.setVisibility(View.GONE);
                    registerButton.setEnabled(true);
                    Toast.makeText(RegistrationActivity.this,
                            "Registration successful! Your Username: " + user.getUserId(),
                            Toast.LENGTH_LONG).show();
                    
                    // Navigate to login
                    Intent intent = new Intent(RegistrationActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    registerButton.setEnabled(true);
                    Toast.makeText(RegistrationActivity.this,
                            "Error saving user data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
