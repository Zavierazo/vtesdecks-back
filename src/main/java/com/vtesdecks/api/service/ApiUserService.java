package com.vtesdecks.api.service;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.configuration.ApiSecurityConfiguration;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.model.api.ApiUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApiUserService {
    private static final long EXPIRATION_TIME = 30L * 24L * 60L * 60L * 1000L;
    private static final long SHORT_EXPIRATION_TIME = 24L * 60L * 60L * 1000L;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ApiUserNotificationService userNotificationService;
    @Autowired
    private ApiSecurityConfiguration securityConfiguration;

    public ApiUser getAuthenticatedUser(DbUser dbUser) {
        ApiUser user = new ApiUser();
        user.setUser(dbUser.getUsername());
        user.setToken(getJWTToken(dbUser, false));
        user.setEmail(dbUser.getEmail());
        user.setAdmin(dbUser.isAdmin());
        user.setTester(dbUser.isTester());
        user.setDisplayName(dbUser.getDisplayName() != null ? dbUser.getDisplayName() : dbUser.getUsername());
        user.setProfileImage(ApiUtils.getProfileImage(dbUser));
        user.setNotificationCount(userNotificationService.notificationUnreadCount(dbUser.getId()));
        return user;
    }

    public String getJWTToken(DbUser user, boolean expireOneDay) {
        List<GrantedAuthority> grantedAuthorities = AuthorityUtils
                .commaSeparatedStringToAuthorityList("USER");
        return Jwts
                .builder()
                .setId(user.getUsername())
                .setSubject(String.valueOf(user.getId()))
                .claim("authorities",
                        grantedAuthorities.stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList()))
                .claim("tester", user.isTester())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + (expireOneDay ? SHORT_EXPIRATION_TIME : EXPIRATION_TIME)))
                .signWith(SignatureAlgorithm.HS512,
                        securityConfiguration.getJwtSecret().getBytes())
                .compact();
    }

}
