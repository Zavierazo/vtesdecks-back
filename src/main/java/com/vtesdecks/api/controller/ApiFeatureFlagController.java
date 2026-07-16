package com.vtesdecks.api.controller;

import com.vtesdecks.model.api.ApiFeatureFlag;
import com.vtesdecks.model.api.ApiFeatureFlagValue;
import com.vtesdecks.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/feature-flag")
@RequiredArgsConstructor
public class ApiFeatureFlagController {
    private final FeatureFlagService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApiFeatureFlag>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ADMIN"})
    public ResponseEntity<ApiFeatureFlag> update(@PathVariable String key, @RequestBody ApiFeatureFlagValue body) {
        try {
            return service.update(key, body.getValue())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
