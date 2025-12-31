package com.vtesdecks.api.controller;

import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.service.DeckArchetypeService;
import com.vtesdecks.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/deck-archetype")
@RequiredArgsConstructor
public class ApiDeckArchetypeController {
    private final DeckArchetypeService service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApiDeckArchetype>> getAll(HttpServletRequest request, @RequestParam(required = false, defaultValue = "TOURNAMENT") MetaType metaType) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean showDisabled = auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ADMIN") || role.equals("MANTAINER"));
        List<ApiDeckArchetype> result = service.getAll(showDisabled, metaType, Utils.getCurrencyCode(request));
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/deck/{deckId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiDeckArchetype> getByDeckId(HttpServletRequest request, @PathVariable String deckId) {
        return service.getByDeckId(deckId, Utils.getCurrencyCode(request))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiDeckArchetype> getById(HttpServletRequest request, @PathVariable Integer id) {
        return service.getById(id, Utils.getCurrencyCode(request)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApiDeckArchetype>> getSuggestions(HttpServletRequest request) {
        List<ApiDeckArchetype> result = service.getSuggestions();
        return ResponseEntity.ok(result);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ADMIN", "MANTAINER"})
    public ResponseEntity<ApiDeckArchetype> create(HttpServletRequest request, @RequestBody ApiDeckArchetype api) {
        return service.create(api, Utils.getCurrencyCode(request))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ADMIN", "MANTAINER"})
    public ResponseEntity<ApiDeckArchetype> update(HttpServletRequest request, @PathVariable Integer id, @RequestBody ApiDeckArchetype api) {
        return service.update(id, api, Utils.getCurrencyCode(request))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping(value = "/{id}")
    @Secured({"ADMIN", "MANTAINER"})
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        boolean deleted = service.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
