package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserPushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPushSubscriptionRepository extends JpaRepository<UserPushSubscriptionEntity, Integer> {
    Optional<UserPushSubscriptionEntity> findByEndpointHash(String endpointHash);

    List<UserPushSubscriptionEntity> findByUser(Integer user);

    long deleteByUserAndEndpointHash(Integer user, String endpointHash);
}
