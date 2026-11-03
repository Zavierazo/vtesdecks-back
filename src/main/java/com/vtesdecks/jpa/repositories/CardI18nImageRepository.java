package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardI18nImageRepository extends JpaRepository<CardI18nImageEntity, CardI18nImageId> {
}
