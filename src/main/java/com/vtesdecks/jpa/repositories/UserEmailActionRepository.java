package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserEmailActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface UserEmailActionRepository extends JpaRepository<UserEmailActionEntity, String> {
    boolean existsByUserIdAndPurposeAndCreatedAtAfter(Integer userId, String purpose, LocalDateTime after);
    void deleteByUserId(Integer userId);
    void deleteByUserIdAndPurpose(Integer userId, String purpose);
    void deleteByExpiresAtLessThanEqual(LocalDateTime now);
}
