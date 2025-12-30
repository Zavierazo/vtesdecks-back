package com.vtesdecks.api.service;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.configuration.ApiSecurityConfiguration;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ApiUserService {
    private static final long EXPIRATION_TIME = 30L * 24L * 60L * 60L * 1000L;
    private static final long SHORT_EXPIRATION_TIME = 24L * 60L * 60L * 1000L;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApiUserNotificationService userNotificationService;
    @Autowired
    private ApiSecurityConfiguration securityConfiguration;

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

}
