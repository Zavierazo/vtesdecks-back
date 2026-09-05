package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.AchievementService;
import com.vtesdecks.api.service.ApiPublicUserService;
import com.vtesdecks.model.api.ApiAchievementFamily;
import com.vtesdecks.model.api.ApiPublicUser;
import com.vtesdecks.model.api.ApiUserOfMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/public/user")
@Slf4j
@RequiredArgsConstructor
public class ApiPublicUserController {
    private final ApiPublicUserService apiPublicUserService;
    private final AchievementService achievementService;

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

    @GetMapping(value = "/{username}/achievements", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApiAchievementFamily>> getAchievements(@PathVariable String username) {
        List<ApiAchievementFamily> achievements = achievementService.getPublic(username);
        return achievements == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(achievements);
    }

    @GetMapping(value = "/top-month", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ResponseEntity<List<ApiUserOfMonth>> getTopUsersOfMonth(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<ApiUserOfMonth> top = apiPublicUserService.getTopUsersOfMonth(year, month);
        return new ResponseEntity<>(top, HttpStatus.OK);
    }
}
