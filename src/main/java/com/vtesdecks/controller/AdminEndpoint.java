package com.vtesdecks.controller;

import com.vtesdecks.scheduler.CardGameGeekScheduler;
import com.vtesdecks.scheduler.CleanUpScheduler;
import com.vtesdecks.scheduler.DriveThruCardsScheduler;
import com.vtesdecks.scheduler.GamePodScheduler;
import com.vtesdecks.scheduler.TournamentDeckScheduler;
import com.vtesdecks.scheduler.VtesdleTodayScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
@Slf4j
public class AdminEndpoint {
    @Autowired
    private CleanUpScheduler cleanUpScheduler;
    @Autowired
    private TournamentDeckScheduler tournamentDeckScheduler;
    @Autowired
    private DriveThruCardsScheduler driveThruCardsScheduler;
    @Autowired
    private GamePodScheduler gamePodScheduler;
    @Autowired
    private VtesdleTodayScheduler vtesdleTodayScheduler;
    @Autowired
    private CardGameGeekScheduler cardGameGeekScheduler;

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/deck_views_clean", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String deckViewCleanScheduler() {
        cleanUpScheduler.deckViewCleanScheduler();
        return "OK";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/deck_clean", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String deckCleanScheduler() {
        cleanUpScheduler.deckCleanScheduler();
        return "OK";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/scrap_decks", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String scrappingDecks() {
        tournamentDeckScheduler.scrappingDecks();
        return "OK";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/dtc", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String driveThruCardsScheduler() {
        driveThruCardsScheduler.scrapCards();
        return "OK";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/gp", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String gamePodScheduler() {
        gamePodScheduler.scrapCards();
        return "OK";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/cgg", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String cardGameGeekScheduler() {
        cardGameGeekScheduler.scrapCards();
        return "OK";
    }

    @RequestMapping(method = RequestMethod.GET, value = "/scheduler/vtesdle_today", produces = {
            MediaType.TEXT_PLAIN_VALUE
    })
    @ResponseBody
    public String vtesdleTodayScheduler() {
        vtesdleTodayScheduler.selectTodayVtesdle();
        return "OK";
    }

}
