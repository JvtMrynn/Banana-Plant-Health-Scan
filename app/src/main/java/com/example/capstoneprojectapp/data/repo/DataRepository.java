package com.example.capstoneprojectapp.data.repo;

import android.content.Context;

import androidx.annotation.WorkerThread;

import com.example.capstoneprojectapp.SessionManager;
import com.example.capstoneprojectapp.data.local.AppDatabase;
import com.example.capstoneprojectapp.data.local.dao.AnalysisHistoryDao;
import com.example.capstoneprojectapp.data.local.dao.DiseaseInfoDao;
import com.example.capstoneprojectapp.data.local.entity.AnalysisHistoryEntity;
import com.example.capstoneprojectapp.data.local.entity.DiseaseInfoEntity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DataRepository {
    private final Context appContext;
    private final DiseaseInfoDao diseaseDao;
    private final AnalysisHistoryDao historyDao;
    private final FirebaseFirestore db;
    private final SessionManager session;
    private static final String PREFS_OFFLINE = "OfflineCachePrefs";
    private static final String KEY_DISEASE_CACHE_SEEDED = "diseaseCacheSeeded";

    public DataRepository(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.get(appContext);
        this.diseaseDao = database.diseaseInfoDao();
        this.historyDao = database.analysisHistoryDao();
        this.db = FirebaseFirestore.getInstance();
        this.session = new SessionManager(appContext);
    }

    // Offline-first for farmer/guest; also refresh from server when possible
    public List<DiseaseInfoEntity> getDiseaseInfoSync() {
        final List<DiseaseInfoEntity> cached = diseaseDao.getAll();
        session.fetchRole(role -> new Thread(this::refreshDiseaseInfoFromServer).start());
        return cached;
    }

    @WorkerThread
    private void refreshDiseaseInfoFromServer() {
        try {
            QuerySnapshot snap = Tasks.await(
                    db.collection("disease_info")
                            .orderBy("diseaseName", Query.Direction.ASCENDING)
                            .get(Source.SERVER)
            );
            List<DocumentSnapshot> docs = snap.getDocuments();
            List<DiseaseInfoEntity> items = new ArrayList<>();
            for (DocumentSnapshot d : docs) {
                DiseaseInfoEntity e = new DiseaseInfoEntity();
                e.id = d.getId();
                e.name = d.getString("diseaseName");
                e.scientificName = d.getString("scientificName");
                e.causedBy = d.getString("causedBy");
                e.symptoms = d.getString("symptoms");
                e.treatment = d.getString("treatment");
                e.prevention = d.getString("prevention");
                Long updatedAt = d.getLong("updatedAt");
                e.updatedAt = updatedAt != null ? updatedAt : 0L;
                items.add(e);
            }
            diseaseDao.clear();
            diseaseDao.upsertAll(items);
            // Mark cache seeded so Splash doesn't require main-thread DB access
            if (!items.isEmpty()) {
                android.content.SharedPreferences sp = appContext.getSharedPreferences(PREFS_OFFLINE, android.content.Context.MODE_PRIVATE);
                sp.edit().putBoolean(KEY_DISEASE_CACHE_SEEDED, true).apply();
            }
        } catch (Exception ignored) { }
    }

    public boolean hasCachedDiseaseInfo() {
        // Use a lightweight flag to avoid main-thread DB queries
        android.content.SharedPreferences sp = appContext.getSharedPreferences(PREFS_OFFLINE, android.content.Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_DISEASE_CACHE_SEEDED, false);
    }

    public DiseaseInfoEntity getLocalDiseaseByName(String name) {
        return diseaseDao.getByName(name);
    }

    // Guest-only local save
    public void saveAnalysisHistoryGuest(String diseaseName, String summary, long timestamp) {
        AnalysisHistoryEntity entity = new AnalysisHistoryEntity();
        entity.localId = UUID.randomUUID().toString();
        entity.remoteId = null;
        entity.userId = "guest";
        entity.diseaseName = diseaseName;
        entity.summary = summary;
        entity.timestamp = timestamp;
        entity.synced = false;
        new Thread(() -> historyDao.insert(entity)).start();
    }

    public List<AnalysisHistoryEntity> getLocalHistory() {
        return historyDao.getAll();
    }

    public int getUnsyncedCount() {
        List<AnalysisHistoryEntity> list = historyDao.getUnsynced();
        return list == null ? 0 : list.size();
    }

    public void syncUnsyncedLocalHistory(com.google.firebase.auth.FirebaseUser user) {
        if (user == null) return;
        new Thread(() -> {
            List<AnalysisHistoryEntity> list = historyDao.getUnsynced();
            if (list == null || list.isEmpty()) return;
            for (AnalysisHistoryEntity e : list) {
                String historyId = UUID.randomUUID().toString();
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("id", historyId);
                payload.put("userId", user.getUid());
                String email = user.getEmail();
                payload.put("userEmail", email);
                payload.put("userName", email != null ? email.split("@")[0] : "User");
                payload.put("diseaseName", e.diseaseName);
                payload.put("confidence", e.summary);
                payload.put("timestamp", e.timestamp);
                try {
                    Tasks.await(db.collection("analysis_history").document(historyId).set(payload));
                    historyDao.markSynced(e.localId, historyId);
                } catch (Exception ignored) { }
            }
        }).start();
    }
}
