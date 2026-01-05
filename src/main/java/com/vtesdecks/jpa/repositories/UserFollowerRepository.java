package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserFollowerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFollowerRepository extends JpaRepository<UserFollowerEntity, UserFollowerEntity.UserFollowerId> {

    boolean existsByIdUserIdAndIdFollowedId(Integer userId, Integer followedId);

    List<UserFollowerEntity> findByIdFollowedId(Integer followedId);
}

