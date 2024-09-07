package com.vtesdecks.service.impl;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.db.CryptMapper;
import com.vtesdecks.db.model.DbCrypt;
import com.vtesdecks.model.DeckExportType;
import com.vtesdecks.service.DeckExportService;
import com.vtesdecks.service.DeckService;

@Service
public class DeckExportServiceImpl implements DeckExportService {
    private static final char TAB = '\t';
    private static final char NEW_LINE = '\n';
    private static final char X = 'x';
    @Autowired
    private DeckService deckService;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private CryptMapper cryptMapper;

    @Override
    public String export(DeckExportType type, String deckId) {
        Deck deck = deckService.getDeck(deckId);
        if (deck == null) {
            throw new IllegalArgumentException("Deck doesn't exists");
        }
        StringBuilder result = new StringBuilder();
        if (type == DeckExportType.LACKEY) {
            exportLackey(result, deck);
        } else if (type == DeckExportType.JOL) {
            exportJOL(result, deck);
        } else if (type == DeckExportType.BCN_CRISIS) {
            exportBCNCrisis(result, deck);
        }
        return result.toString();
    }

    private void exportLackey(StringBuilder result, Deck deck) {
        for (Map.Entry<String, List<Card>> entry : deck.getLibraryByType().entrySet()) {
            for (Card card : entry.getValue()) {
                Library library = libraryCache.get(card.getId());
                if (library != null) {
                    result.append(card.getNumber()).append(TAB).append(StringUtils.trim(library.getName())).append(NEW_LINE);
                }
            }
        }
        result.append("Crypt:").append(NEW_LINE);
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                result.append(card.getNumber()).append(TAB).append(StringUtils.trim(crypt.getName())).append(crypt.isAdv() ? " (ADV)" : "")
                    .append(NEW_LINE);
            }
        }
    }

    private void exportJOL(StringBuilder result, Deck deck) {
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                result.append(card.getNumber()).append(X).append(StringUtils.trim(crypt.getName())).append(crypt.isAdv() ? " (ADV)" : "")
                    .append(NEW_LINE);
            }
        }
        result.append(NEW_LINE);
        for (Map.Entry<String, List<Card>> entry : deck.getLibraryByType().entrySet()) {
            for (Card card : entry.getValue()) {
                Library library = libraryCache.get(card.getId());
                if (library != null) {
                    result.append(card.getNumber()).append(X).append(StringUtils.trim(library.getName())).append(NEW_LINE);
                }
            }
        }
    }

    private void exportBCNCrisis(StringBuilder result, Deck deck) {
        result.append("Deck Name: ").append(deck.getName()).append(NEW_LINE);
        result.append("Author: ").append(deck.getAuthor()).append(NEW_LINE);
        result.append("Description: ").append(deck.getDescription() != null ? deck.getDescription() : EMPTY).append(NEW_LINE).append(NEW_LINE);
        result.append("Crypt(")
            .append(deck.getStats().getCrypt())
            .append(" cards; Capacity min=")
            .append(deck.getCrypt().stream().map(Card::getId).map(cryptCache::get).mapToInt(Crypt::getCapacity).min().orElse(0))
            .append(" max=")
            .append(deck.getCrypt().stream().map(Card::getId).map(cryptCache::get).mapToInt(Crypt::getCapacity).max().orElse(0))
            .append(" avg=")
            .append(deck.getCrypt().stream().map(Card::getId).map(cryptCache::get).mapToInt(Crypt::getCapacity).average().orElse(0))
            .append(")").append(NEW_LINE);
        result.append("==================").append(NEW_LINE);
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                DbCrypt dbCrypt = cryptMapper.selectById(crypt.getId());
                result.append(card.getNumber()).append("x").append(SPACE)
                    .append(StringUtils.trim(crypt.getName())).append(SPACE)
                    .append(crypt.getCapacity()).append(SPACE)
                    .append(dbCrypt.getDisciplines()).append(SPACE)
                    .append(crypt.getClan()).append(":").append(crypt.getGroup() < 0 ? "ANY" : crypt.getGroup()).append(SPACE)
                    .append(NEW_LINE);
            }
        }
        result.append(NEW_LINE);
        result.append("Library: ").append(deck.getStats().getLibrary()).append(" cards").append(NEW_LINE).append(NEW_LINE);
        for (Map.Entry<String, List<Card>> entry : deck.getLibraryByType().entrySet()) {
            result.append(entry.getKey()).append(" (").append(entry.getValue().stream().map(Card::getNumber).reduce(0, Integer::sum))
                .append(" cards)").append(NEW_LINE);
            result.append("==================").append(NEW_LINE);
            for (Card card : entry.getValue()) {
                Library library = libraryCache.get(card.getId());
                if (library != null) {
                    result.append(card.getNumber()).append("x").append(SPACE).append(StringUtils.trim(library.getName())).append(NEW_LINE);
                }
            }
            result.append(NEW_LINE);
        }
    }
}
