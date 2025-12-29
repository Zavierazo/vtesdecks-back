package com.vtesdecks.api.controller;

import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.service.DeckArchetypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/deck-archetype")
@RequiredArgsConstructor
public class ApiDeckArchetypeController {

    private final DeckArchetypeService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApiDeckArchetype>> getAll() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ADMIN"::equals);
        List<ApiDeckArchetype> result = isAdmin ? service.getAll() : service.getAllActive();
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/deck/{deckId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiDeckArchetype> getByDeckId(@PathVariable String deckId) {
        return service.getByDeckId(deckId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiDeckArchetype> getById(@PathVariable Integer id) {
        return service.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ADMIN"})
    public ResponseEntity<ApiDeckArchetype> create(@RequestBody ApiDeckArchetype api) {
        ApiDeckArchetype created = service.create(api);
        return ResponseEntity.ok(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ADMIN"})
    public ResponseEntity<ApiDeckArchetype> update(@PathVariable Integer id, @RequestBody ApiDeckArchetype api) {
        return service.update(id, api)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping(value = "/{id}")
    @Secured({"ADMIN"})
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        boolean deleted = service.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
