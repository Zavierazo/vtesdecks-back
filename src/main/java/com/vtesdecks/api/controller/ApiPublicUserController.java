package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiPublicUserService;
import com.vtesdecks.model.api.ApiPublicUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/1.0/public/user")
@Slf4j
@RequiredArgsConstructor
public class ApiPublicUserController {
    private final ApiPublicUserService apiPublicUserService;

    @GetMapping(value = "/{username}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiPublicUser getPublicUser(@PathVariable String username) {
        return apiPublicUserService.getPublicUser(username);
    }
}
