package com.vtesdecks.service.impl;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.model.DeckExportType;
import com.vtesdecks.model.Discipline;
import com.vtesdecks.service.DeckExportService;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

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
        } else if (type == DeckExportType.TWD || type == DeckExportType.BCN_CRISIS) {
            exportTWD(result, deck);
        }
        return result.toString();
    }

    private void exportLackey(StringBuilder result, Deck deck) {
        for (Map.Entry<String, List<Card>> entry : deck.getLibraryByType().entrySet()) {
            for (Card card : entry.getValue()) {
                Library library = libraryCache.get(card.getId());
                if (library != null) {
                    result
                            .append(card.getNumber())
                            .append(TAB)
                            .append(Utils.normalizeLackeyName(library.getName()))
                            .append(NEW_LINE);
                }
            }
        }
        result.append("Crypt:").append(NEW_LINE);
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                result
                        .append(card.getNumber())
                        .append(TAB)
                        .append(Utils.normalizeLackeyName(getCryptName(crypt)))
                        .append(NEW_LINE);
            }
        }
    }

    private void exportJOL(StringBuilder result, Deck deck) {
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                result.append(card.getNumber()).append(X).append(getCryptName(crypt))
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

    private void exportTWD(StringBuilder result, Deck deck) {
        result.append("Deck Name: ").append(deck.getName()).append(NEW_LINE);
        result.append("Author: ").append(deck.getAuthor()).append(NEW_LINE);
        result.append("Description: ").append(deck.getDescription() != null ? deck.getDescription().replaceAll("<[^>]*>", "") : EMPTY).append(NEW_LINE).append(NEW_LINE);
        String cryptTitle = getCryptTitle(deck);
        result.append(cryptTitle).append(NEW_LINE);
        result.append("-".repeat(cryptTitle.length())).append(NEW_LINE);
        int numberSpaces = 0;
        int nameSpaces = 0;
        int capacitySpaces = 0;
        int disciplinesSpaces = 6;
        int titleSpaces = 0;
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                numberSpaces = Math.max(numberSpaces, String.valueOf(card.getNumber()).length());
                nameSpaces = Math.max(nameSpaces, getCryptName(crypt).length());
                capacitySpaces = Math.max(capacitySpaces, String.valueOf(crypt.getCapacity()).length());
                int superiorDisciplineSpaces = crypt.getSuperiorDisciplines() != null ? crypt.getSuperiorDisciplines().stream()
                        .map(Discipline::getFromName)
                        .filter(Objects::nonNull)
                        .map(d -> d.getAlias()[0])
                        .mapToInt(alias -> alias.length() + 1)
                        .sum() : 0;
                int inferiorDisciplineSpaces = crypt.getDisciplines() != null ? crypt.getDisciplines().stream()
                        .filter(discipline -> crypt.getSuperiorDisciplines() == null || !crypt.getSuperiorDisciplines().contains(discipline))
                        .map(Discipline::getFromName)
                        .filter(Objects::nonNull)
                        .map(d -> d.getAlias()[0])
                        .mapToInt(alias -> alias.length() + 1)
                        .sum() : 0;
                disciplinesSpaces = Math.max(disciplinesSpaces, superiorDisciplineSpaces + inferiorDisciplineSpaces);
                titleSpaces = Math.max(titleSpaces, crypt.getTitle() != null ? crypt.getTitle().length() + 2 : 0);
            }
        }
        for (Card card : deck.getCrypt()) {
            Crypt crypt = cryptCache.get(card.getId());
            if (crypt != null) {
                String number = String.valueOf(card.getNumber());
                result.append(number).append("x").append(" ".repeat(Math.max(0, numberSpaces - number.length()))).append(SPACE);
                String name = getCryptName(crypt);
                result.append(name).append(" ".repeat(Math.max(0, nameSpaces - name.length()))).append(SPACE).append(SPACE);
                String capacity = String.valueOf(crypt.getCapacity());
                result.append(" ".repeat(Math.max(0, capacitySpaces - capacity.length()))).append(capacity).append(SPACE).append(SPACE);
                StringBuilder disciplines = new StringBuilder();
                if (crypt.getSuperiorDisciplines() != null) {
                    for (String superiorDiscipline : crypt.getSuperiorDisciplines()) {
                        if (!disciplines.isEmpty()) {
                            disciplines.append(" ");
                        }
                        Discipline discipline = Discipline.getFromName(superiorDiscipline);
                        if (discipline != null) {
                            disciplines.append(StringUtils.upperCase(discipline.getAlias()[0]));
                        }
                    }
                }
                if (crypt.getDisciplines() != null) {
                    for (String inferiorDiscipline : crypt.getDisciplines()) {
                        if (crypt.getSuperiorDisciplines() != null && crypt.getSuperiorDisciplines().contains(inferiorDiscipline)) {
                            continue;
                        }
                        if (!disciplines.isEmpty()) {
                            disciplines.append(" ");
                        }
                        Discipline discipline = Discipline.getFromName(inferiorDiscipline);
                        if (discipline != null) {
                            disciplines.append(StringUtils.lowerCase(discipline.getAlias()[0]));
                        }
                    }
                }
                if (disciplines.isEmpty()) {
                    disciplines = new StringBuilder("-none-");
                }
                result.append(disciplines).append(" ".repeat(Math.max(0, disciplinesSpaces - disciplines.length()))).append(SPACE);
                String title = crypt.getTitle() != null ? StringUtils.lowerCase(crypt.getTitle()) : "";
                result.append(title);
                if (titleSpaces > 2) {
                    result.append(" ".repeat(Math.max(0, titleSpaces - title.length())));
                }
                result.append(crypt.getClan()).append(":").append(crypt.getGroup() < 0 ? "ANY" : crypt.getGroup());
                result.append(NEW_LINE);
            }
        }
        result.append(NEW_LINE);
        result.append("Library (").append(deck.getStats().getLibrary()).append(" cards)").append(NEW_LINE);
        for (Map.Entry<String, List<Card>> entry : deck.getLibraryByType().entrySet()) {
            result.append(entry.getKey()).append(" (").append(entry.getValue().stream().map(Card::getNumber).reduce(0, Integer::sum));
            if (entry.getKey().equals("Master")) {
                int trifle = 0;
                for (Card card : entry.getValue()) {
                    Library library = libraryCache.get(card.getId());
                    if (library != null && library.isTrifle()) {
                        trifle++;
                    }
                }
                if (trifle > 0) {
                    result.append("; ").append(trifle).append(" trifle");
                }
            }
            result.append(")").append(NEW_LINE);
            for (Card card : entry.getValue()) {
                Library library = libraryCache.get(card.getId());
                if (library != null) {
                    result.append(card.getNumber()).append("x").append(SPACE).append(StringUtils.trim(library.getName())).append(NEW_LINE);
                }
            }
            result.append(NEW_LINE);
        }
    }

    private static String getCryptName(Crypt crypt) {
        return StringUtils.trim(crypt.getName()) + (crypt.isAdv() ? " (ADV)" : "");
    }

    private String getCryptTitle(Deck deck) {
        return "Crypt(" +
                deck.getStats().getCrypt() +
                " cards, min=" +
                deck.getStats().getMinCrypt() +
                ", max=" +
                deck.getStats().getMaxCrypt() +
                ", avg=" +
                deck.getStats().getAvgCrypt() +
                ")";
    }
}
