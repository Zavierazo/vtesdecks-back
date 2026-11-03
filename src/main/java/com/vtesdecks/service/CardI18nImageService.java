package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.jpa.repositories.CardI18nImageRepository;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vtesdecks.service.I18nImageClient.Availability.MISSING;
import static com.vtesdecks.service.I18nImageClient.Availability.PRESENT;

@Service
@RequiredArgsConstructor
public class CardI18nImageService {
    private final CardI18nImageRepository imageRepository;
    private final CryptRepository cryptRepository;
    private final LibraryRepository libraryRepository;

    public record Changes(int added, int updated, int removed, boolean cryptChanged, boolean libraryChanged) {
    }

    @Transactional
    public Changes synchronize(List<I18nImageClient.Check> checks) {
        // Re-read the catalog after the network checks, since imports may have removed cards meanwhile.
        Set<Integer> cryptIds = new HashSet<>(cryptRepository.selectIds());
        Set<Integer> libraryIds = new HashSet<>(libraryRepository.selectIds());
        Set<Integer> cardIds = new HashSet<>(cryptIds);
        cardIds.addAll(libraryIds);
        Map<CardI18nImageId, CardI18nImageEntity> current = imageRepository.findAll().stream()
                .collect(Collectors.toMap(CardI18nImageEntity::getId, Function.identity()));
        List<CardI18nImageEntity> saves = new ArrayList<>();
        List<CardI18nImageEntity> deletes = new ArrayList<>();
        Set<Integer> changedIds = new HashSet<>();
        int added = 0;
        int updated = 0;
        for (I18nImageClient.Check check : checks) {
            if (!cardIds.contains(check.id().getCardId())) {
                continue;
            }
            CardI18nImageEntity existing = current.get(check.id());
            if (check.availability() == PRESENT) {
                String image = I18nImageClient.imagePath(check.id());
                if (existing == null) {
                    existing = new CardI18nImageEntity();
                    existing.setId(check.id());
                    existing.setImage(image);
                    saves.add(existing);
                    changedIds.add(check.id().getCardId());
                    added++;
                } else if (!image.equals(existing.getImage())) {
                    existing.setImage(image);
                    saves.add(existing);
                    changedIds.add(check.id().getCardId());
                    updated++;
                }
            } else if (check.availability() == MISSING && existing != null) {
                deletes.add(existing);
                changedIds.add(check.id().getCardId());
            }
        }
        current.values().stream()
                .filter(image -> !cardIds.contains(image.getId().getCardId()))
                .forEach(deletes::add);
        if (!saves.isEmpty()) {
            imageRepository.saveAll(saves);
        }
        if (!deletes.isEmpty()) {
            imageRepository.deleteAllInBatch(deletes);
        }
        cryptIds.retainAll(changedIds);
        libraryIds.retainAll(changedIds);
        // Keep lastUpdate observable even when deleting the image removes its own modification timestamp.
        if (!cryptIds.isEmpty()) {
            cryptRepository.touchModificationDate(cryptIds);
        }
        if (!libraryIds.isEmpty()) {
            libraryRepository.touchModificationDate(libraryIds);
        }
        return new Changes(added, updated, deletes.size(), !cryptIds.isEmpty(), !libraryIds.isEmpty());
    }
}
