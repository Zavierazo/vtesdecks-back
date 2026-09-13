package com.vtesdecks.cache.indexable.deck;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entity.UserEntity;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class DeckUser {
    Integer id;
    String username;
    String displayName;
    String profileImage;
    List<String> roles;

    public static DeckUser from(UserEntity user, List<String> roles) {
        if (user == null) {
            return null;
        }
        return new DeckUser(user.getId(), user.getUsername(), user.getDisplayName(),
                ApiUtils.getProfileImage(user), roles == null ? List.of() : List.copyOf(roles));
    }
}
