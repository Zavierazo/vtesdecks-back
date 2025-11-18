package com.vtesdecks.scheduler;

import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.integration.ArchonClient;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.model.archon.TournamentEvent;
import com.vtesdecks.model.archon.TournamentsResponse;
import com.vtesdecks.util.VtesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TournamentArchonDeckScheduler {

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CryptRepository cryptRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private DeckCardRepository deckCardRepository;

    @Autowired
    private DeckIndex deckIndex;

    @Autowired
    private ArchonClient archonClient;

    //    @Scheduled(cron = "${jobs.scrappingArchonDecksCron:0 30 0 * * *}")
//    @Scheduled(initialDelay = 0L, fixedDelay = 1000000L)
    //TODO: Currently disabled, no way to obtain tournament decks from Archon
    @Transactional
    public void scrappingArchonDecks() {
        log.info("Starting tournament decks scrapping...");
        try {
            TournamentsResponse response = archonClient.getTournaments("Finished", null, null);
            parseEvents(response.getEvents());
            do {
                response = archonClient.getTournaments("Finished", response.getUid(), response.getDate());
                parseEvents(response.getEvents());
            } while (response.getUid() != null
                    && response.getDate() != null
                    && response.getEvents() != null
                    && !response.getEvents().isEmpty()
                    && response.getEvents().getFirst().getStart() != null
                    && response.getEvents().getFirst().getStart().getYear() >= LocalDate.now().getYear());

        } catch (Exception e) {
            log.error("Unable to scan decks", e);
        }
        log.info("Finished to scan");
    }

    private void parseEvents(List<TournamentEvent> events) {
        for (TournamentEvent event : events) {
            try {
                parseEvent(event);
            } catch (Exception e) {
                log.error("Unable to parse event {} with uid {}", event.getName(), event.getUid(), e);
            }

        }

    }

    private void parseEvent(TournamentEvent event) {
        log.info("Parsing event {} with uid {}", event.getName(), event.getUid());
    }

    private boolean isValidDeck(DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        int crypt = 0;
        int library = 0;
        for (DeckCardEntity card : deckCards.values()) {
            if (VtesUtils.isCrypt(card.getId().getCardId())) {
                crypt += card.getNumber();
            } else if (VtesUtils.isLibrary(card.getId().getCardId())) {
                library += card.getNumber();
            }
        }
        if (crypt >= 12 && library >= 60 && library <= 90) {
            return true;
        } else if (deck.getYear() < 2015 && crypt >= 11 && library >= 59 && library <= 91) {
            //crypt of 59/91 because some old decks are have illegal amount of cards....
            //library of 11 because some old decks are have illegal amount of cards....
            return true;
        } else {
            log.error("Invalid number of cards for deck {}. Crypt {} Library {}", deck.getId(), crypt, library);
            return false;
        }
    }
}
