package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiArchonService;
import com.vtesdecks.model.api.ApiArchonAuthorization;
import com.vtesdecks.model.api.ApiArchonCallback;
import com.vtesdecks.model.api.ApiArchonStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/1.0/user/archon")
@RequiredArgsConstructor
public class ApiArchonController {
    private final ApiArchonService service;

    @GetMapping
    public ResponseEntity<ApiArchonStatus> status() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.status());
    }

    @PostMapping("/connect")
    public ResponseEntity<ApiArchonAuthorization> connect() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.connect());
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiArchonStatus> complete(@RequestBody ApiArchonCallback callback) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.complete(callback));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        service.disconnect();
        return ResponseEntity.noContent().build();
    }
}
