package com.vtesdecks.api.service;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.configuration.ApiSecurityConfiguration;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.entity.UserFollowerEntity;
import com.vtesdecks.jpa.repositories.UserFollowerRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUserService {
    private static final long EXPIRATION_TIME = 30L * 24L * 60L * 60L * 1000L;
    private static final long SHORT_EXPIRATION_TIME = 24L * 60L * 60L * 1000L;
    private final UserRepository userRepository;
    private final ApiUserNotificationService userNotificationService;
    private final ApiSecurityConfiguration securityConfiguration;
    private final UserFollowerRepository userFollowerRepository;

    public ApiUser getAuthenticatedUser(UserEntity dbUser, List<String> roles) {
        ApiUser user = new ApiUser();
        user.setUser(dbUser.getUsername());
        user.setToken(getJWTToken(dbUser, roles, false));
        user.setEmail(dbUser.getEmail());
        user.setAdmin(dbUser.getAdmin() != null && dbUser.getAdmin());
        user.setRoles(roles);
        user.setDisplayName(dbUser.getDisplayName() != null ? dbUser.getDisplayName() : dbUser.getUsername());
        user.setProfileImage(ApiUtils.getProfileImage(dbUser));
        user.setNotificationCount(userNotificationService.notificationUnreadCount(dbUser.getId()));
        return user;
    }

    public String getJWTToken(UserEntity user, List<String> roles, boolean expireOneDay) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        grantedAuthorities.add(new SimpleGrantedAuthority("USER"));
        if (user.getAdmin() != null && user.getAdmin()) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ADMIN"));
        }
        for (String role : roles) {
            grantedAuthorities.add(new SimpleGrantedAuthority(StringUtils.upperCase(role)));
        }
        return Jwts
                .builder()
                .id(user.getUsername())
                .subject(String.valueOf(user.getId()))
                .claim("authorities",
                        grantedAuthorities.stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList())
                .claim("tester", roles.contains("tester"))
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + (expireOneDay ? SHORT_EXPIRATION_TIME : EXPIRATION_TIME)))
                .signWith(Keys.hmacShaKeyFor(securityConfiguration.getJwtSecret().getBytes()))
                .compact();

    }

    /**
     * Follow or unfollow a user
     *
     * @param userId The user who is following
     * @param user   The user to be followed/unfollowed
     * @param follow True to follow, False to unfollow
     * @return True if operation was successful
     */
    public Boolean followUser(Integer userId, String user, Boolean follow) {
        if (userId == null || user == null || follow == null) {
            log.warn("Invalid parameters for followUser: userId={}, followedId={}, follow={}", userId, user, follow);
            return false;
        }

        // Check if both users exist
        UserEntity followedUser = userRepository.findByUsername(user);
        if (!userRepository.existsById(userId) || followedUser == null) {
            log.warn("One or both users do not exist: userId={}, followedId={}", userId, user);
            return false;
        }

        // Cannot follow yourself
        if (userId.equals(followedUser.getId())) {
            log.warn("User {} attempted to follow themselves", userId);
            return false;
        }


        UserFollowerEntity.UserFollowerId id = new UserFollowerEntity.UserFollowerId(userId, followedUser.getId());
        if (follow) {
            // Follow user
            if (!userFollowerRepository.existsById(id)) {
                UserFollowerEntity userFollower = new UserFollowerEntity();
                userFollower.setId(id);
                userFollowerRepository.save(userFollower);
                log.info("User {} is now following user {}", userId, user);
                return true;
            } else {
                log.debug("User {} is already following user {}", userId, user);
                return true; // Already following
            }
        } else {
            // Unfollow user
            if (userFollowerRepository.existsById(id)) {
                userFollowerRepository.deleteById(id);
                log.info("User {} unfollowed user {}", userId, user);
                return true;
            } else {
                log.debug("User {} was not following user {}", userId, user);
                return true; // Already not following
            }
        }
    }

    /**
     * Check if a user follows another user
     *
     * @param userId The user who might be following
     * @param user   The user who might be followed
     * @return True if userId follows followedId
     */
    public Boolean isFollowing(Integer userId, String user) {
        if (userId == null || user == null) {
            return false;
        }
        UserEntity followedUser = userRepository.findByUsername(user);
        if (followedUser == null) {
            return false;
        }

        return userFollowerRepository.existsByIdUserIdAndIdFollowedId(userId, followedUser.getId());
    }

}
