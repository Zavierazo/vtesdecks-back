package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CryptI18nEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptI18nRepository extends JpaRepository<CryptI18nEntity, CryptI18nEntity.CryptI18nId> {

}