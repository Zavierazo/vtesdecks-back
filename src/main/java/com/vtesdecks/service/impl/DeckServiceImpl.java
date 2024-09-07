package com.vtesdecks.service.impl;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.service.DeckService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DeckServiceImpl implements DeckService {
    @Autowired
    private DeckIndex deckIndex;

    @Override
    public ResultSet<Deck> getDecks(com.vtesdecks.model.DeckType deckType,
                                    DeckSort order,
                                    Integer userId,
                                    String name,
                                    String author,
                                    String cardText,
                                    List<String> clans,
                                    List<String> disciplines,
                                    List<String> cards,
                                    List<Integer> cryptSize,
                                    List<Integer> librarySize,
                                    List<Integer> group,
                                    Boolean starVampire,
                                    Boolean singleClan,
                                    Boolean singleDiscipline,
                                    List<Integer> year,
                                    List<Integer> players,
                                    String master,
                                    String action,
                                    String political,
                                    String retainer,
                                    String equipment,
                                    String ally,
                                    String modifier,
                                    String combat,
                                    String reaction,
                                    String event,
                                    Boolean absoluteProportion,
                                    List<String> tags,
                                    Boolean favorite) {
        DeckQuery.DeckQueryBuilder builder = DeckQuery.builder()
                .order(order)
                .user(userId)
                .name(name)
                .author(author)
                .cardText(cardText)
                .clans(clans)
                .disciplines(disciplines)
                .starVampire(starVampire)
                .singleClan(singleClan)
                .singleDiscipline(singleDiscipline)
                .tags(tags)
                .favorite(favorite);
        if (cards != null && !cards.isEmpty()) {
            Map<Integer, Integer> cardMap = new HashMap<>();
            for (String card : cards) {
                int indexEqual = card.indexOf('=');
                int number = 1;
                int id;
                if (indexEqual > 0) {
                    id = Integer.parseInt(card.substring(0, indexEqual));
                    number = Integer.parseInt(card.substring(indexEqual + 1));
                } else {
                    id = Integer.parseInt(card);
                }
                cardMap.put(id, number);
            }
            builder = builder.cards(cardMap);
        }
        if (cryptSize != null && cryptSize.size() == 2) {
            Integer cryptSizeMin = cryptSize.get(0);
            if (cryptSizeMin <= 12) {
                cryptSizeMin = null;
            }
            Integer cryptSizeMax = cryptSize.get(1);
            if (cryptSizeMax >= 40) {
                cryptSizeMax = null;
            }
            builder = builder
                    .cryptSizeMin(cryptSizeMin)
                    .cryptSizeMax(cryptSizeMax);
        }
        if (librarySize != null && librarySize.size() == 2) {
            Integer librarySizeMin = librarySize.get(0);
            if (librarySizeMin <= 60) {
                librarySizeMin = null;
            }
            Integer librarySizeMax = librarySize.get(1);
            if (librarySizeMax >= 90) {
                librarySizeMax = null;
            }
            builder = builder
                    .librarySizeMin(librarySizeMin)
                    .librarySizeMax(librarySizeMax);
        }
        if (deckType != null && deckType != com.vtesdecks.model.DeckType.ALL) {
            builder = builder.type(DeckType.valueOf(deckType.name()));
        }
        if (group != null && group.size() == 2) {
            int minGroup = group.get(0);
            int maxGroup = group.get(1);
            List<Integer> groups = new ArrayList<>();
            if (minGroup == 0) {
                groups.add(-1);
                minGroup++;
            }
            for (int i = minGroup; i <= maxGroup; i++) {
                groups.add(i);
            }
            builder = builder.groups(groups);
        }
        if (year != null && year.size() == 2) {
            Integer minYear = year.get(0);
            if (minYear <= 1998) {
                minYear = null;
            }
            Integer maxYear = year.get(1);
            if (maxYear >= LocalDate.now().getYear()) {
                maxYear = null;
            }
            builder = builder
                    .minYear(minYear)
                    .maxYear(maxYear);
        }
        if (players != null && players.size() == 2) {
            Integer minPlayers = players.get(0);
            if (minPlayers <= 10) {
                minPlayers = null;
            }
            Integer maxPlayers = players.get(1);
            if (maxPlayers >= 200) {
                maxPlayers = null;
            }
            builder = builder
                    .minPlayers(minPlayers)
                    .maxPlayers(maxPlayers);
        }
        if (absoluteProportion != null && absoluteProportion) {
            builder = builder.proportionType(DeckQuery.ProportionType.ABSOLUTE);
        } else {
            builder = builder.proportionType(DeckQuery.ProportionType.PERCENTAGE);
        }
        if (master != null && !master.equalsIgnoreCase("ANY")) {
            builder = builder.master(getCardProportion(master));
        }
        if (action != null && !action.equalsIgnoreCase("ANY")) {
            builder = builder.action(getCardProportion(action));
        }
        if (political != null && !political.equalsIgnoreCase("ANY")) {
            builder = builder.political(getCardProportion(political));
        }
        if (retainer != null && !retainer.equalsIgnoreCase("ANY")) {
            builder = builder.retainer(getCardProportion(retainer));
        }
        if (equipment != null && !equipment.equalsIgnoreCase("ANY")) {
            builder = builder.equipment(getCardProportion(equipment));
        }
        if (ally != null && !ally.equalsIgnoreCase("ANY")) {
            builder = builder.ally(getCardProportion(ally));
        }
        if (modifier != null && !modifier.equalsIgnoreCase("ANY")) {
            builder = builder.modifier(getCardProportion(modifier));
        }
        if (combat != null && !combat.equalsIgnoreCase("ANY")) {
            builder = builder.combat(getCardProportion(combat));
        }
        if (reaction != null && !reaction.equalsIgnoreCase("ANY")) {
            builder = builder.reaction(getCardProportion(reaction));
        }
        if (event != null && !event.equalsIgnoreCase("ANY")) {
            builder = builder.event(getCardProportion(event));
        }
        return deckIndex.selectAll(builder.build());
    }

    @Nullable
    private DeckQuery.CardProportion getCardProportion(String value) {
        try {
            String[] percentageSplit = value.split(",");
            return new DeckQuery.CardProportion(Integer.parseInt(percentageSplit[0]), Integer.parseInt(percentageSplit[1]));
        } catch (Exception e) {
            log.error("Unable to parse percentage with value {}", value, e);
        }
        return null;
    }

    @Override
    public Deck getDeck(String deckId) {
        return deckIndex.get(deckId);
    }
}
