package com.example.capstoneprojectapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import android.content.Intent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiseaseInfoManagementActivity extends AppCompatActivity 
        implements DiseaseInfoAdapter.OnDiseaseActionListener {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private FloatingActionButton fabAdd;
    private BottomNavigationView bottomNavigation;
    private DiseaseInfoAdapter adapter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_info_management);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupBottomNavigation();
        setupClickListeners();
        loadDiseaseInfo();

        // Determine role to set read-only mode for admins
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    isAdmin = User.ROLE_ADMIN.equals(role);
                    if (isAdmin) {
                        fabAdd.setVisibility(View.GONE);
                        if (adapter != null) adapter.setReadOnly(true);
                    }
                });
        }
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        fabAdd = findViewById(R.id.fabAdd);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new DiseaseInfoAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupBottomNavigation() {
        // Highlight current item
        bottomNavigation.setSelectedItemId(R.id.nav_disease_info);
        
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                // Go to appropriate dashboard
                Class<?> target = isAdmin ? AdminDashboardActivity.class : ExpertDashboardActivity.class;
                startActivity(new Intent(DiseaseInfoManagementActivity.this, target));
                finish();
                return true;
            } else if (itemId == R.id.nav_disease_info) {
                // Already on this screen
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(DiseaseInfoManagementActivity.this, AnalysisHistoryActivity.class);
                intent.putExtra("EXPERT_MODE", true);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_consultations) {
                Intent intent = new Intent(DiseaseInfoManagementActivity.this, ConsultationRequestsActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(DiseaseInfoManagementActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }
    
    private void setupClickListeners() {
        fabAdd.setOnClickListener(v -> showAddDiseaseDialog());
    }

    private void loadDiseaseInfo() {
        db.collection("disease_info")
                .orderBy("diseaseName")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DiseaseInfo> diseaseList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        DiseaseInfo disease = document.toObject(DiseaseInfo.class);
                        diseaseList.add(disease);
                    }
                    
                    adapter.setDiseaseList(diseaseList);
                    
                    if (diseaseList.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading disease info: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showAddDiseaseDialog() {
        showDiseaseDialog(null);
    }

    private void showDiseaseDialog(DiseaseInfo existingDisease) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_disease_info, null);

        TextInputEditText etDiseaseName = dialogView.findViewById(R.id.etDiseaseName);
        TextInputEditText etScientificName = dialogView.findViewById(R.id.etScientificName);
        TextInputEditText etCausedBy = dialogView.findViewById(R.id.etCausedBy);
        TextInputEditText etSymptoms = dialogView.findViewById(R.id.etSymptoms);
        TextInputEditText etTreatment = dialogView.findViewById(R.id.etTreatment);
        TextInputEditText etPrevention = dialogView.findViewById(R.id.etPrevention);

        // If editing, populate fields
        if (existingDisease != null) {
            etDiseaseName.setText(existingDisease.getDiseaseName());
            etScientificName.setText(existingDisease.getScientificName());
            etCausedBy.setText(existingDisease.getCausedBy());
            etSymptoms.setText(existingDisease.getSymptoms());
            etTreatment.setText(existingDisease.getTreatment());
            etPrevention.setText(existingDisease.getPrevention());
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existingDisease == null ? "Add Disease Information" : "Edit Disease Information")
                .setView(dialogView)
                .setPositiveButton("Save", null) // we'll override to prevent auto-dismiss on invalid
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(d -> {
            android.widget.Button saveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v -> {
                String diseaseName = etDiseaseName.getText().toString().trim();
                String scientificName = etScientificName.getText().toString().trim();
                String causedBy = etCausedBy.getText().toString().trim();
                String symptoms = etSymptoms.getText().toString().trim();
                String treatment = etTreatment.getText().toString().trim();
                String prevention = etPrevention.getText().toString().trim();

                if (validateInput(diseaseName, causedBy, symptoms, treatment, prevention)) {
                    if (existingDisease == null) {
                        addDiseaseInfo(diseaseName, scientificName, causedBy, symptoms, treatment, prevention);
                    } else {
                        updateDiseaseInfo(existingDisease.getId(), diseaseName, scientificName, causedBy,
                                symptoms, treatment, prevention);
                    }
                    dialog.dismiss();
                }
                // if invalid, keep dialog open and show toasts (handled in validateInput)
            });
        });

        dialog.show();
    }

    private boolean validateInput(String diseaseName, String causedBy, String symptoms, 
                                  String treatment, String prevention) {
        if (TextUtils.isEmpty(diseaseName)) {
            Toast.makeText(this, "Please enter disease name", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(causedBy)) {
            Toast.makeText(this, "Please enter the cause", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(symptoms)) {
            Toast.makeText(this, "Please enter symptoms", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(treatment)) {
            Toast.makeText(this, "Please enter treatment", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(prevention)) {
            Toast.makeText(this, "Please enter prevention methods", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void addDiseaseInfo(String diseaseName, String scientificName, String causedBy, String symptoms, 
                                String treatment, String prevention) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String diseaseId = UUID.randomUUID().toString();
        String expertName = currentUser.getEmail() != null ? 
                currentUser.getEmail().split("@")[0] : "Expert";

        DiseaseInfo diseaseInfo = new DiseaseInfo(
                diseaseId,
                diseaseName,
                scientificName,
                causedBy,
                symptoms,
                treatment,
                prevention,
                currentUser.getUid(),
                expertName
        );

        db.collection("disease_info")
                .document(diseaseId)
                .set(diseaseInfo)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Disease information added successfully", 
                            Toast.LENGTH_SHORT).show();
                    loadDiseaseInfo();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error adding disease info: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateDiseaseInfo(String diseaseId, String diseaseName, String scientificName, String causedBy, 
                                   String symptoms, String treatment, String prevention) {
        db.collection("disease_info")
                .document(diseaseId)
                .update(
                        "diseaseName", diseaseName,
                        "scientificName", scientificName,
                        "causedBy", causedBy,
                        "symptoms", symptoms,
                        "treatment", treatment,
                        "prevention", prevention,
                        "updatedAt", System.currentTimeMillis()
                )
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Disease information updated successfully",
                            Toast.LENGTH_SHORT).show();
                    loadDiseaseInfo();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating disease info: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onEdit(DiseaseInfo diseaseInfo) {
        showDiseaseDialog(diseaseInfo);
    }

    @Override
    public void onDelete(DiseaseInfo diseaseInfo) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Disease Information")
                .setMessage("Are you sure you want to delete information about " + 
                        diseaseInfo.getDiseaseName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("disease_info")
                            .document(diseaseInfo.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Disease information deleted",
                                        Toast.LENGTH_SHORT).show();
                                loadDiseaseInfo();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error deleting: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
