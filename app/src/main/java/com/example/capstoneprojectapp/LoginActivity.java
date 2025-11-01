package com.example.capstoneprojectapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton, guestLoginButton;
    private TextView registerTextView;
    private CheckBox keepLoggedInCheckBox;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_KEEP_LOGGED_IN = "keepLoggedIn";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_GUEST = "isGuest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Check if user should be kept logged in
        if (checkKeepLoggedIn()) {
            autoNavigateBasedOnSavedRole();
            return;
        }

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        guestLoginButton = findViewById(R.id.guestLoginButton);
        registerTextView = findViewById(R.id.registerTextView);
        keepLoggedInCheckBox = findViewById(R.id.keepLoggedInCheckBox);
        progressBar = findViewById(R.id.progressBar);

        loginButton.setOnClickListener(v -> loginUser());
        
        guestLoginButton.setOnClickListener(v -> loginAsGuest());

        registerTextView.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegistrationActivity.class);
            startActivity(intent);
        });
    }

    private boolean checkKeepLoggedIn() {
        boolean keepLoggedIn = sharedPreferences.getBoolean(KEY_KEEP_LOGGED_IN, false);
        boolean isGuest = sharedPreferences.getBoolean(KEY_IS_GUEST, false);
        
        if (keepLoggedIn) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // Guest user or regular user
                if (isGuest && currentUser.isAnonymous()) {
                    return true;
                }
                // Regular user with email
                String savedEmail = sharedPreferences.getString(KEY_USER_EMAIL, null);
                if (savedEmail != null && currentUser.getEmail() != null && 
                    currentUser.getEmail().equals(savedEmail)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void autoNavigateBasedOnSavedRole() {
        // Get saved role from SharedPreferences
        String savedRole = sharedPreferences.getString("userRole", User.ROLE_FARMER);
        navigateBasedOnRole(savedRole);
    }

    private void loginUser() {
        String identifier = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(identifier)) {
            emailEditText.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        loginButton.setEnabled(false);

        // Authenticate with Firebase
        // Determine if identifier is an email or username (userId)
        if (identifier.contains("@")) {
            signInWithEmail(identifier, password);
        } else {
            // Lookup email by username (userId) in public login_lookup
            resolveEmailFromUsername(identifier, resolvedEmail -> {
                if (resolvedEmail == null) {
                    progressBar.setVisibility(View.GONE);
                    loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Username not found", Toast.LENGTH_LONG).show();
                } else {
                    signInWithEmail(resolvedEmail, password);
                }
            });
        }
    }

    private interface EmailCallback { void onResult(String email); }

    private void resolveEmailFromUsername(String username, EmailCallback cb) {
        String key = "u_" + username.trim().toLowerCase();
        FirebaseFirestore.getInstance()
                .collection("login_lookup")
                .document(key)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String email = doc.getString("email");
                        cb.onResult(email);
                    } else {
                        cb.onResult(null);
                    }
                })
                .addOnFailureListener(e -> cb.onResult(null));
    }

    private void signInWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Fetch user data from Firestore
                            fetchUserData(user.getUid(), email);
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, 
                                "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void fetchUserData(String uid, String email) {
        db.collection("users").document(uid)
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    loginButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String userId = document.getString("userId");
                            String role = document.getString("role");
                            String status = document.getString("status");

                            // Block login if account is not approved
                            if (status == null || !"APPROVED".equals(status)) {
                                Toast.makeText(LoginActivity.this,
                                        "Your account is pending admin approval.",
                                        Toast.LENGTH_LONG).show();
                                // Ensure not remembered and sign out
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putBoolean(KEY_KEEP_LOGGED_IN, false);
                                editor.remove(KEY_USER_EMAIL);
                                editor.remove(KEY_USER_ID);
                                editor.remove("userRole");
                                editor.apply();
                                mAuth.signOut();
                                return;
                            }
                            
                            // Save login state if checkbox is checked
                            if (keepLoggedInCheckBox.isChecked()) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putBoolean(KEY_KEEP_LOGGED_IN, true);
                                editor.putString(KEY_USER_EMAIL, email);
                                editor.putString(KEY_USER_ID, userId);
                                editor.putString("userRole", role);
                                editor.apply();
                            }

                            Toast.makeText(LoginActivity.this, "Login successful!", 
                                    Toast.LENGTH_SHORT).show();
                            
                            // Log login event for admin auditing
                            logLoginEvent(uid, email, role);
                            
                            // Navigate based on role
                    navigateBasedOnRole(role);
                    // Enqueue background sync once signed in (if any offline analyses exist)
                    com.example.capstoneprojectapp.sync.HistorySyncWorker.enqueue(LoginActivity.this);
                        } else {
                            Toast.makeText(LoginActivity.this, 
                                    "User data not found in database",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, 
                                "Error fetching user data: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateBasedOnRole(String role) {
        Intent intent;
        
        if (User.ROLE_ADMIN.equals(role)) {
            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        } else if (User.ROLE_EXPERT.equals(role)) {
            intent = new Intent(LoginActivity.this, ExpertDashboardActivity.class);
        } else {
            // Default to Farmer Dashboard
            intent = new Intent(LoginActivity.this, FarmerDashboardActivity.class);
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void logLoginEvent(String uid, String email, String role) {
        try {
            java.util.Map<String, Object> evt = new java.util.HashMap<>();
            evt.put("userId", uid);
            evt.put("email", email);
            evt.put("role", role);
            evt.put("timestamp", System.currentTimeMillis());
            db.collection("login_events").add(evt);
        } catch (Exception ignored) {
        }
    }

    private void navigateToFarmerDashboard() {
        Intent intent = new Intent(LoginActivity.this, FarmerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loginAsGuest() {
        progressBar.setVisibility(View.VISIBLE);
        guestLoginButton.setEnabled(false);
        loginButton.setEnabled(false);

        // Sign in anonymously with Firebase
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    guestLoginButton.setEnabled(true);
                    loginButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Always mark as guest, but only persist if keep logged in is checked
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putBoolean(KEY_IS_GUEST, true);
                            editor.putString(KEY_USER_ID, "GUEST_" + user.getUid().substring(0, 8));
                            
                            if (keepLoggedInCheckBox.isChecked()) {
                                editor.putBoolean(KEY_KEEP_LOGGED_IN, true);
                            } else {
                                editor.putBoolean(KEY_KEEP_LOGGED_IN, false);
                            }
                            editor.apply();

                            Toast.makeText(LoginActivity.this, 
                                    "Logged in as Guest", 
                                    Toast.LENGTH_SHORT).show();
                            navigateBasedOnRole(User.ROLE_FARMER);
                        }
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Guest login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && checkKeepLoggedIn()) {
            autoNavigateBasedOnSavedRole();
        }
    }
}
