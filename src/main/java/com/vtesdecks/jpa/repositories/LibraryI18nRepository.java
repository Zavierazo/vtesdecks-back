package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.LibraryI18nEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryI18nRepository extends JpaRepository<LibraryI18nEntity, LibraryI18nEntity.LibraryI18nId> {

}