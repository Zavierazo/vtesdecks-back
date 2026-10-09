package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.ArchonUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchonUserRepository extends JpaRepository<ArchonUserEntity, String> {
}
