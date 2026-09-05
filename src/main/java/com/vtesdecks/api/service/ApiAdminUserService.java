package com.vtesdecks.api.service;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAdminUser;
import com.vtesdecks.model.api.ApiAdminUserAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAdminUserService {
    private static final String ROLE_CATALOG_SQL = "SELECT DISTINCT name FROM role ORDER BY name";
    private static final String DELETE_USER_ROLES_SQL = "DELETE FROM user_role WHERE user_id = ?";
    private static final String INSERT_USER_ROLE_SQL = """
            INSERT INTO user_role (user_id, role_id)
            SELECT ?, MIN(id)
            FROM role
            WHERE name = ?
            HAVING MIN(id) IS NOT NULL
            """;

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordResetService passwordResetService;

    @Transactional(readOnly = true)
    public Optional<ApiAdminUser> get(String identifier) {
        return Optional.ofNullable(findUser(identifier)).map(this::map);
    }

    @Transactional
    public Optional<ApiAdminUser> updateAccess(String identifier, ApiAdminUserAccess access, Integer actorUserId) {
        UserEntity user = findUser(identifier);
        if (user == null) {
            return Optional.empty();
        }
        if (access == null || access.getAdmin() == null || access.getRoles() == null) {
            throw new IllegalArgumentException("Admin and roles are required");
        }

        Set<String> requestedRoles = new LinkedHashSet<>(access.getRoles());
        if (requestedRoles.contains(null)) {
            throw new IllegalArgumentException("Role names cannot be null");
        }
        List<String> availableRoles = getRoleCatalog();
        if (!availableRoles.containsAll(requestedRoles)) {
            throw new IllegalArgumentException("Unknown role name");
        }

        user.setAdmin(access.getAdmin());
        userRepository.save(user);
        jdbcTemplate.update(DELETE_USER_ROLES_SQL, user.getId());
        for (String role : requestedRoles) {
            int inserted = jdbcTemplate.update(INSERT_USER_ROLE_SQL, user.getId(), role);
            if (inserted != 1) {
                throw new IllegalArgumentException("Unknown role name: " + role);
            }
        }
        log.info("Admin access updated actorUserId={} targetUsername={} admin={} roles={}",
                actorUserId, user.getUsername(), access.getAdmin(), requestedRoles);
        List<String> savedRoles = new ArrayList<>(requestedRoles);
        savedRoles.sort(String::compareTo);
        return Optional.of(map(user, savedRoles, availableRoles));
    }

    @Transactional
    public Optional<ApiAdminUser> validate(String identifier, Integer actorUserId) {
        UserEntity user = findUser(identifier);
        if (user == null) {
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(user.getValidated())) {
            user.setValidated(true);
            userRepository.save(user);
        }
        log.info("Admin validated account actorUserId={} targetUsername={}", actorUserId, user.getUsername());
        return Optional.of(map(user));
    }

    @Transactional
    public PasswordResetService.Result sendPasswordReset(String identifier, Integer actorUserId) {
        UserEntity user = findUser(identifier);
        if (user == null) {
            return PasswordResetService.Result.USER_NOT_FOUND;
        }
        PasswordResetService.Result result = passwordResetService.request(user);
        log.info("Admin requested password reset actorUserId={} targetUsername={} result={}",
                actorUserId, user.getUsername(), result);
        return result;
    }

    private ApiAdminUser map(UserEntity user) {
        List<String> roles = new ArrayList<>(new LinkedHashSet<>(userRepository.selectRolesByUserId(user.getId())));
        roles.sort(String::compareTo);
        return map(user, roles, getRoleCatalog());
    }

    private UserEntity findUser(String identifier) {
        UserEntity user = userRepository.findByUsername(identifier);
        return user != null ? user : userRepository.findByEmailIgnoreCase(identifier);
    }

    private ApiAdminUser map(UserEntity user, List<String> roles, List<String> availableRoles) {
        return ApiAdminUser.builder()
                .user(user.getUsername())
                .displayName(user.getDisplayName())
                .profileImage(ApiUtils.getProfileImage(user))
                .email(user.getEmail())
                .validated(user.getValidated())
                .admin(user.getAdmin())
                .roles(roles)
                .availableRoles(availableRoles)
                .build();
    }

    private List<String> getRoleCatalog() {
        return jdbcTemplate.queryForList(ROLE_CATALOG_SQL, String.class);
    }
}
