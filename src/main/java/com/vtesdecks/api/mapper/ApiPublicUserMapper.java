package com.vtesdecks.api.mapper;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.model.api.ApiUser;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ApiPublicUserMapper {

    @Named("mapDeckUser")
    public ApiUser mapUser(Deck deck) {
        return mapUser(deck.getUser(), deck.getUserRoles());
    }

    public ApiUser mapUser(UserEntity user, List<String> roles) {
        if (user == null) {
            return null;
        }
        ApiUser apiUser = new ApiUser();
        apiUser.setUser(user.getUsername());
        apiUser.setDisplayName(user.getDisplayName());
        apiUser.setProfileImage(ApiUtils.getProfileImage(user));
        apiUser.setAdmin(user.getAdmin());
        apiUser.setRoles(roles);
        return apiUser;
    }
}
