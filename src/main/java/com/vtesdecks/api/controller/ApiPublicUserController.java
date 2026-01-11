package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiPublicUserService;
import com.vtesdecks.model.api.ApiPublicUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiPublicUser> getPublicUser(@PathVariable String username) {
        ApiPublicUser publicUser = apiPublicUserService.getPublicUser(username);
        if (publicUser == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(publicUser, HttpStatus.OK);
        }
    }
}
