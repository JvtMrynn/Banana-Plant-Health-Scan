package com.example.capstoneprojectapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.capstoneprojectapp.data.local.entity.AnalysisHistoryEntity;

import java.util.List;

@Dao
public interface AnalysisHistoryDao {
    @Query("SELECT * FROM analysis_history ORDER BY timestamp DESC")
    List<AnalysisHistoryEntity> getAll();

    @Insert
    void insert(AnalysisHistoryEntity item);

    @Query("SELECT * FROM analysis_history WHERE synced = 0")
    List<AnalysisHistoryEntity> getUnsynced();

    @Query("UPDATE analysis_history SET synced = 1, remoteId = :remoteId WHERE localId = :localId")
    void markSynced(String localId, String remoteId);
}

