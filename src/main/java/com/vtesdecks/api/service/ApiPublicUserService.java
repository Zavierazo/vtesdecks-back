package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiPublicUserMapper;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.entity.UserFollowerEntity;
import com.vtesdecks.jpa.repositories.UserFollowerRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiPublicUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPublicUserService {
    private final UserRepository userRepository;
    private final UserFollowerRepository userFollowerRepository;
    private final ApiPublicUserMapper apiPublicUserMapper;

    public ApiPublicUser getPublicUser(String username) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            return null;
        }
        List<String> roles = userRepository.selectRolesByUserId(user.getId());

        ApiPublicUser publicUser = apiPublicUserMapper.mapPublicUser(user, roles);
        publicUser.setFollowing(new ArrayList<>());
        for (UserFollowerEntity follower : userFollowerRepository.findByIdUserId(user.getId())) {
            publicUser.getFollowing().add(apiPublicUserMapper.mapPublicUser(follower.getFollowed(), Collections.emptyList()));
        }
        publicUser.setFollowers(new ArrayList<>());
        for (UserFollowerEntity follower : userFollowerRepository.findByIdFollowedId(user.getId())) {
            publicUser.getFollowers().add(apiPublicUserMapper.mapPublicUser(follower.getUser(), Collections.emptyList()));
        }
        return publicUser;
    }

}
