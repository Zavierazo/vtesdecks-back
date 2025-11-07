package com.vtesdecks.api.util;

import com.vtesdecks.configuration.ApiConstants;
import com.vtesdecks.jpa.entity.UserEntity;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class ApiUtils {

    public static Integer extractUserId() {
        String userId = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        if (userId.equals(ApiConstants.NON_LOGGED_USER)) {
            return null;
        }
        return Integer.parseInt(userId);
    }

    public static String getProfileImage(UserEntity user) {
        String hash = DigestUtils.md5Hex(StringUtils.lowerCase(StringUtils.trim(user.getEmail())));
        return user.getProfileImage() != null ? user.getProfileImage()
                : "https://www.gravatar.com/avatar/" + hash + "?d=https://vtesdecks.com/assets/img/default_user.png";
    }

    public static String generatePublicHash() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
