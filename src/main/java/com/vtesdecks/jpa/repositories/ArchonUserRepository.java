package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.ArchonUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchonUserRepository extends JpaRepository<ArchonUserEntity, String> {
    List<PlayerName> findAllProjectedBy();

    interface PlayerName {
        String getName();

        String getVeknId();
    }
}
