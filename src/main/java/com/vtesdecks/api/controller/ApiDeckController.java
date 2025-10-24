package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiDeckService;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.model.DeckExportType;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.model.DeckTag;
import com.vtesdecks.model.DeckType;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiDeckHome;
import com.vtesdecks.model.api.ApiDeckView;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.service.DeckExportService;
import com.vtesdecks.util.Utils;
import com.vtesdecks.worker.DeckFeedbackWorker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/api/1.0/decks")
@Slf4j
public class ApiDeckController {
    @Autowired
    private ApiDeckService deckService;
    @Autowired
    private DeckExportService deckExportService;
    @Autowired
    private DeckFeedbackWorker deckFeedbackWorker;
    ;
    @Autowired
    private UserMapper userMapper;

    @RequestMapping(method = RequestMethod.GET, value = "/home", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiDeckHome> home() {
        ApiDeckHome apiDeckHome = new ApiDeckHome();
        apiDeckHome.setPreConstructedTotal(decks(0, DeckType.PRECONSTRUCTED, DeckSort.NEWEST, null).getTotal());
        if (ApiUtils.extractUserId() != null) {
            apiDeckHome.setUserTotal(decks(0, DeckType.USER, DeckSort.NEWEST, null).getTotal());
            apiDeckHome.setFavoriteTotal(decks(0, DeckType.ALL, DeckSort.NEWEST, Boolean.TRUE).getTotal());
        }
        ApiDecks tournamentPopular = decks(6, DeckType.TOURNAMENT, DeckSort.POPULAR, null);
        apiDeckHome.setTournamentPopular(tournamentPopular.getDecks());
        ApiDecks tournamentNewest = decks(6, DeckType.TOURNAMENT, DeckSort.NEWEST, null);
        apiDeckHome.setTournamentNewest(tournamentNewest.getDecks());
        ApiDecks communityPopular = decks(6, DeckType.COMMUNITY, DeckSort.POPULAR, null);
        apiDeckHome.setCommunityPopular(communityPopular.getDecks());
        ApiDecks communityNewest = decks(6, DeckType.COMMUNITY, DeckSort.NEWEST, null);
        apiDeckHome.setCommunityNewest(communityNewest.getDecks());
        apiDeckHome.setTournamentTotal(tournamentPopular.getTotal());
        apiDeckHome.setCommunityTotal(communityPopular.getTotal());
        return new ResponseEntity<>(apiDeckHome, HttpStatus.OK);
    }

    private ApiDecks decks(Integer limit, DeckType type, DeckSort order, Boolean favorite) {
        return deckService.getDecks(type, order, ApiUtils.extractUserId(), null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, favorite, 0, limit);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiDeck> deck(@PathVariable String id, @RequestParam(required = false, defaultValue = "false") boolean collectionTracker) {
        ApiDeck deck = deckService.getDeck(id, true, collectionTracker);
        if (deck == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(deck, HttpStatus.OK);
        }
    }


    @RequestMapping(method = RequestMethod.GET, produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiDecks> decks(@RequestParam(name = "type", required = false, defaultValue = "ALL") DeckType type,
                                          @RequestParam(name = "order", required = false, defaultValue = "NEWEST") DeckSort order,
                                          @RequestParam(name = "name", required = false) String name,
                                          @RequestParam(name = "author", required = false) String author,
                                          @RequestParam(name = "cardText", required = false) String cardText,
                                          @RequestParam(name = "clans", required = false) List<String> clans,
                                          @RequestParam(name = "disciplines", required = false) List<String> disciplines,
                                          @RequestParam(name = "cards", required = false) List<String> cards,
                                          @RequestParam(name = "cryptSize", required = false) List<Integer> cryptSize,
                                          @RequestParam(name = "librarySize", required = false) List<Integer> librarySize,
                                          @RequestParam(name = "group", required = false) List<Integer> group,
                                          @RequestParam(name = "starVampire", required = false) Boolean starVampire,
                                          @RequestParam(name = "singleClan", required = false) Boolean singleClan,
                                          @RequestParam(name = "singleDiscipline", required = false) Boolean singleDiscipline,
                                          @RequestParam(name = "year", required = false) List<Integer> year,
                                          @RequestParam(name = "players", required = false) List<Integer> players,
                                          @RequestParam(name = "master", required = false) String master,
                                          @RequestParam(name = "action", required = false) String action,
                                          @RequestParam(name = "political", required = false) String political,
                                          @RequestParam(name = "retainer", required = false) String retainer,
                                          @RequestParam(name = "equipment", required = false) String equipment,
                                          @RequestParam(name = "ally", required = false) String ally,
                                          @RequestParam(name = "modifier", required = false) String modifier,
                                          @RequestParam(name = "combat", required = false) String combat,
                                          @RequestParam(name = "reaction", required = false) String reaction,
                                          @RequestParam(name = "event", required = false) String event,
                                          @RequestParam(name = "absoluteProportion", required = false) Boolean absoluteProportion,
                                          @RequestParam(name = "tags", required = false) List<String> tags,
                                          @RequestParam(name = "favorite", required = false) Boolean favorite,
                                          @RequestParam(name = "limitedFormat", required = false) String limitedFormat,
                                          @RequestParam(name = "paths", required = false) List<String> paths,
                                          @RequestParam(name = "bySimilarity", required = false) String bySimilarity,
                                          @RequestParam(name = "collectionPercentage", required = false) Integer collectionPercentage,
                                          @RequestParam(name = "offset", required = false) Integer offset,
                                          @RequestParam(name = "limit", required = false) Integer limit) {
        ApiDecks decks = deckService.getDecks(type, order, ApiUtils.extractUserId(), name, author, cardText, clans, disciplines, cards, cryptSize,
                librarySize, group, starVampire, singleClan, singleDiscipline, year, players, master, action, political, retainer, equipment, ally,
                modifier, combat, reaction, event, absoluteProportion, tags, limitedFormat, paths, bySimilarity, collectionPercentage, favorite, offset != null ? offset : 0, limit != null ? limit : 20);
        return new ResponseEntity<>(decks, HttpStatus.OK);
    }


    @RequestMapping(value = "/{id}/export", method = RequestMethod.GET)
    public void getDownload(HttpServletResponse response, @PathVariable("id") String id, @RequestParam(name = "type") DeckExportType type, HttpServletRequest httpServletRequest) {
        log.info("Deck export of {} with type {}, userAgent: '{}', ip: '{}'", id, type, httpServletRequest.getHeader("User-Agent"), Utils.getIp(httpServletRequest));
        Utils.returnFile(response, StringUtils.lowerCase(Utils.removeSpecial(id) + "_" + Utils.removeSpecial(type.name()) + ".txt"), MediaType.TEXT_PLAIN,
                deckExportService.export(type, id));
    }

    @RequestMapping(method = RequestMethod.POST, value = "/{id}/view", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<Boolean> view(@PathVariable String id, @RequestBody ApiDeckView deckView, HttpServletRequest httpServletRequest) {
        DbUser user = null;
        Integer userId = ApiUtils.extractUserId();
        if (userId != null) {
            user = userMapper.selectById(ApiUtils.extractUserId());
        }
        deckFeedbackWorker.enqueueView(id, user, deckView.getSource(), httpServletRequest);
        return new ResponseEntity<>(true, HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/tags", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<String>> deckTags() {
        return new ResponseEntity<>(Arrays.stream(DeckTag.values()).map(DeckTag::getTag).toList(), HttpStatus.OK);
    }
}
