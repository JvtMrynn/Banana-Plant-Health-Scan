package com.example.capstoneprojectapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.capstoneprojectapp.data.local.entity.DiseaseInfoEntity;

import java.util.List;

@Dao
public interface DiseaseInfoDao {
    @Query("SELECT * FROM disease_info ORDER BY name")
    List<DiseaseInfoEntity> getAll();

    @Query("SELECT * FROM disease_info WHERE name = :name LIMIT 1")
    DiseaseInfoEntity getByName(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<DiseaseInfoEntity> items);

    @Query("DELETE FROM disease_info")
    void clear();
}

