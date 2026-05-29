package com.vtesdecks.api.mapper;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.model.api.ApiPublicUser;
import com.vtesdecks.model.api.ApiUserOfMonth;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ApiPublicUserMapper {

    @Named("mapDeckUser")
    public ApiPublicUser mapUser(Deck deck) {
        return mapPublicUser(deck.getUser(), deck.getUserRoles());
    }

    public ApiPublicUser mapPublicUser(UserEntity user, List<String> roles) {
        if (user == null) {
            return null;
        }
        return ApiPublicUser.builder()
                .user(user.getUsername())
                .displayName(user.getDisplayName())
                .profileImage(ApiUtils.getProfileImage(user))
                .roles(roles)
                .build();
    }

    public ApiUserOfMonth mapUserOfMonth(UserEntity user, List<String> roles, Integer rank, Long score) {
        if (user == null) {
            return null;
        }
        return ApiUserOfMonth.builder()
                .user(user.getUsername())
                .displayName(user.getDisplayName())
                .profileImage(ApiUtils.getProfileImage(user))
                .roles(roles)
                .rank(rank)
                .score(score)
                .build();
    }
}
