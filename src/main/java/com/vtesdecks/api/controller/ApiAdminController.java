package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiAdminSchedulerService;
import com.vtesdecks.api.service.ApiAdminUserService;
import com.vtesdecks.api.service.PasswordResetService;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.model.api.ApiAdminScheduler;
import com.vtesdecks.model.api.ApiAdminUser;
import com.vtesdecks.model.api.ApiAdminUserAccess;
import com.vtesdecks.model.api.ApiFeatureFlag;
import com.vtesdecks.model.api.ApiFeatureFlagValue;
import com.vtesdecks.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/admin")
@RequiredArgsConstructor
@Secured("ADMIN")
public class ApiAdminController {
    private final ApiAdminUserService userService;
    private final ApiAdminSchedulerService schedulerService;
    private final FeatureFlagService featureFlagService;

    @GetMapping(value = "/users/{identifier}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiAdminUser> getUser(@PathVariable String identifier) {
        return userService.get(identifier)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/users/{identifier}/access", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiAdminUser> updateUserAccess(@PathVariable String identifier,
                                                          @RequestBody ApiAdminUserAccess access) {
        try {
            return userService.updateAccess(identifier, access, ApiUtils.extractUserId())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/users/{identifier}/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiAdminUser> validateUser(@PathVariable String identifier) {
        return userService.validate(identifier, ApiUtils.extractUserId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/users/{identifier}/password-reset")
    public ResponseEntity<Void> sendUserPasswordReset(@PathVariable String identifier) {
        PasswordResetService.Result result = userService.sendPasswordReset(identifier, ApiUtils.extractUserId());
        return switch (result) {
            case SENT -> ResponseEntity.noContent().build();
            case COOLDOWN -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            case USER_NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

    @PutMapping(value = "/feature-flags/{key}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiFeatureFlag> updateFeatureFlag(@PathVariable String key,
                                                             @RequestBody ApiFeatureFlagValue body) {
        try {
            return featureFlagService.update(key, body.getValue())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping(value = "/schedulers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApiAdminScheduler>> getSchedulers() {
        return ResponseEntity.ok(schedulerService.getAll());
    }

    @PostMapping(value = "/schedulers/{key}")
    public ResponseEntity<Void> runScheduler(@PathVariable String key) {
        return schedulerService.run(key, ApiUtils.extractUserId())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
