package com.vtesdecks.api.mapper;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.model.api.ApiPublicUser;
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
        ApiPublicUser apiUser = new ApiPublicUser();
        apiUser.setUser(user.getUsername());
        apiUser.setDisplayName(user.getDisplayName());
        apiUser.setProfileImage(ApiUtils.getProfileImage(user));
        apiUser.setRoles(roles);
        return apiUser;
    }
}
