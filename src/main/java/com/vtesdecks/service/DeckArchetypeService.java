package com.vtesdecks.service;

import com.vtesdecks.api.mapper.DeckArchetypeMapper;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.model.api.ApiDeckArchetype;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeckArchetypeService {

    private final DeckArchetypeRepository repository;
    private final DeckArchetypeMapper mapper;

    public List<ApiDeckArchetype> getAll() {
        return mapper.map(repository.findAll());
    }

    public List<ApiDeckArchetype> getAllActive() {
        return mapper.map(repository.findByEnabledTrue());
    }

    public Optional<ApiDeckArchetype> getById(Integer id) {
        return repository.findById(id).map(mapper::map);
    }

    public Optional<ApiDeckArchetype> getByDeckId(String deckId) {
        Optional<DeckArchetypeEntity> entity = repository.findByDeckId(deckId);
        return entity.map(mapper::map);
    }

    public ApiDeckArchetype create(ApiDeckArchetype api) {
        DeckArchetypeEntity entity = mapper.map(api);
        DeckArchetypeEntity saved = repository.save(entity);
        return mapper.map(saved);
    }

    public Optional<ApiDeckArchetype> update(Integer id, ApiDeckArchetype api) {
        Optional<DeckArchetypeEntity> maybe = repository.findById(id);
        if (!maybe.isPresent()) return Optional.empty();
        DeckArchetypeEntity entity = maybe.get();
        // copy fields
        entity.setName(api.getName());
        entity.setIcon(api.getIcon());
        entity.setType(api.getType());
        entity.setDescription(api.getDescription());
        entity.setDeckId(api.getDeckId());
        entity.setEnabled(api.getEnabled());
        DeckArchetypeEntity saved = repository.save(entity);
        return Optional.of(mapper.map(saved));
    }

    public boolean delete(Integer id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
