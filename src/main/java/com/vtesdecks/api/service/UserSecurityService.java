package com.vtesdecks.api.service;

import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import com.vtesdecks.jpa.repositories.UserEmailActionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserSecurityService {
    private final UserRepository users;
    private final UserEmailActionRepository emailActions;

    @Transactional(readOnly = true)
    public UsernamePasswordAuthenticationToken authenticate(Claims claims, boolean rejectLegacy) {
        UserEntity user = users.findById(Integer.valueOf(claims.getSubject()))
                .orElseThrow(() -> new JwtException("Account unavailable"));
        if (!Boolean.TRUE.equals(user.getValidated())) {
            throw new JwtException("Account unavailable");
        }
        boolean hasVersion = claims.containsKey("auth_version");
        boolean hasPurpose = claims.containsKey("token_use");
        if (!hasVersion && !hasPurpose) {
            if (rejectLegacy || user.getAuthVersion() != 0 || claims.getIssuedAt() == null
                    || claims.getExpiration() == null
                    || Math.abs(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()
                    - 30L * 24 * 60 * 60 * 1000) > 1000) {
                throw new JwtException("Legacy token rejected");
            }
        } else {
            Object version = claims.get("auth_version");
            if (!"access".equals(claims.get("token_use"))
                    || !(version instanceof Integer || version instanceof Long)
                    || ((Number) version).longValue() != user.getAuthVersion()) {
                throw new JwtException("Token revoked or invalid");
            }
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("USER"));
        if (Boolean.TRUE.equals(user.getAdmin())) {
            authorities.add(new SimpleGrantedAuthority("ADMIN"));
        }
        users.selectRolesByUserId(user.getId()).forEach(role ->
                authorities.add(new SimpleGrantedAuthority(role.toUpperCase(Locale.ROOT))));
        var authentication = new UsernamePasswordAuthenticationToken(String.valueOf(user.getId()), null, authorities);
        return authentication;
    }

    @Transactional
    public void revoke(UserEntity user) {
        user.setAuthVersion(user.getAuthVersion() + 1);
        users.save(user);
        emailActions.deleteByUserId(user.getId());
    }
}
