package com.vtesdecks.controller;

import com.vtesdecks.scheduler.CleanUpScheduler;
import com.vtesdecks.scheduler.DeckArchetypeScheduler;
import com.vtesdecks.scheduler.ProxyCardOptionScheduler;
import com.vtesdecks.scheduler.TournamentDeckScheduler;
import com.vtesdecks.scheduler.VtesdleTodayScheduler;
import com.vtesdecks.scheduler.shops.CardGameGeekScheduler;
import com.vtesdecks.scheduler.shops.DriveThruCardsScheduler;
import com.vtesdecks.scheduler.shops.GamePodScheduler;
import com.vtesdecks.scheduler.shops.MarketScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminEndpoint {
    private final CleanUpScheduler cleanUpScheduler;
    private final TournamentDeckScheduler tournamentDeckScheduler;
    private final DriveThruCardsScheduler driveThruCardsScheduler;
    private final GamePodScheduler gamePodScheduler;
    private final VtesdleTodayScheduler vtesdleTodayScheduler;
    private final CardGameGeekScheduler cardGameGeekScheduler;
    private final ProxyCardOptionScheduler proxyCardOptionScheduler;
    private final MarketScheduler marketScheduler;
    private final DeckArchetypeScheduler deckArchetypeScheduler;

    @GetMapping(value = "/scheduler/deck_views_clean", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String deckViewCleanScheduler() {
        cleanUpScheduler.deckViewCleanScheduler();
        return "OK";
    }

    @GetMapping(value = "/scheduler/deck_clean", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String deckCleanScheduler() {
        cleanUpScheduler.deckCleanScheduler();
        return "OK";
    }

    @GetMapping(value = "/scheduler/scrap_decks", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String scrappingDecks() {
        tournamentDeckScheduler.scrappingDecks();
        return "OK";
    }

    @GetMapping(value = "/scheduler/dtc", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String driveThruCardsScheduler() {
        driveThruCardsScheduler.scrapCards();
        return "OK";
    }

    @GetMapping(value = "/scheduler/gp", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String gamePodScheduler() {
        gamePodScheduler.scrapCards();
        return "OK";
    }

    @GetMapping(value = "/scheduler/cgg", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String cardGameGeekScheduler() {
        cardGameGeekScheduler.scrapCards();
        return "OK";
    }

    @GetMapping(value = "/scheduler/market", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String marketScheduler() {
        marketScheduler.scrapCards();
        return "OK";
    }

    @GetMapping(value = "/scheduler/vtesdle_today", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String vtesdleTodayScheduler() {
        vtesdleTodayScheduler.selectTodayVtesdle();
        return "OK";
    }

    @GetMapping(value = "/scheduler/proxyCardOption", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String proxyCardOption() {
        proxyCardOptionScheduler.proxyCardOptionScheduler();
        return "OK";
    }

    @GetMapping(value = "/scheduler/deckArchetype", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    public String deckArchetypeScheduler() {
        deckArchetypeScheduler.deckArchetypeScheduler();
        return "OK";
    }


}
