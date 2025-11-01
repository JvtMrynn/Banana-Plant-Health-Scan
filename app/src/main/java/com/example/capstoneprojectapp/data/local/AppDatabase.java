package com.example.capstoneprojectapp.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.capstoneprojectapp.data.local.dao.AnalysisHistoryDao;
import com.example.capstoneprojectapp.data.local.dao.DiseaseInfoDao;
import com.example.capstoneprojectapp.data.local.entity.AnalysisHistoryEntity;
import com.example.capstoneprojectapp.data.local.entity.DiseaseInfoEntity;

@Database(
        entities = {DiseaseInfoEntity.class, AnalysisHistoryEntity.class},
        version = 2,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract DiseaseInfoDao diseaseInfoDao();
    public abstract AnalysisHistoryDao analysisHistoryDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "banana.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
