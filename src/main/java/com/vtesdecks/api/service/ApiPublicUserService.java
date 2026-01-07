package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiPublicUserMapper;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPublicUserService {
    private final UserRepository userRepository;
    private final ApiPublicUserMapper apiPublicUserMapper;

    public ApiUser getPublicUser(String username) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            return null;
        }
        List<String> roles = userRepository.selectRolesByUserId(user.getId());
        return apiPublicUserMapper.mapUser(user, roles);
    }

}
