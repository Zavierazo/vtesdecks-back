package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    UserEntity findByEmail(String email);

    UserEntity findByUsername(String username);

    List<UserEntity> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String usernamePart, String displayNamePart);

    @Query(value = "SELECT r.name FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE ur.user_id = :id", nativeQuery = true)
    List<String> selectRolesByUserId(Integer id);
}