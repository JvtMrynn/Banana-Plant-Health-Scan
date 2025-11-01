package com.example.capstoneprojectapp;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SessionManager {
    private final Context appContext;

    public SessionManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isSignedIn() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && !user.isAnonymous();
    }

    public boolean isGuest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null || user.isAnonymous();
    }

    public interface RoleCallback { void onRole(String role); }

    public void fetchRole(RoleCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { cb.onRole("GUEST"); return; }
        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    cb.onRole(role != null ? role : User.ROLE_FARMER);
                })
                .addOnFailureListener(e -> cb.onRole(User.ROLE_FARMER));
    }
}

