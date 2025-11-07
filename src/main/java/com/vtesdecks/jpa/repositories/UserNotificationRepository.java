package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserNotificationEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Integer> {

    UserNotificationEntity findByReferenceId(Integer referenceId);

    @Query(value = "SELECT COUNT(1) FROM user_notification WHERE user = :user AND `read` IS FALSE", nativeQuery = true)
    int countUnreadByUser(Integer user);

    List<UserNotificationEntity> findByUserOrderByCreationDateDesc(Integer user);

    @Modifying
    @Transactional
    @Query(value = "UPDATE user_notification SET `read` = true WHERE user = :user", nativeQuery = true)
    void updateReadAllByUser(Integer user);

    @Modifying
    @Transactional
    void deleteByReferenceId(Integer referenceId);
}