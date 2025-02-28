package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiDeckBuilderService;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.model.ImportType;
import com.vtesdecks.model.api.ApiDeckBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@Controller
@RequestMapping("/api/1.0/user/decks/builder")
@Slf4j
public class ApiDeckBuilderController {
    @Autowired
    private ApiDeckBuilderService deckBuilderService;

    @RequestMapping(method = RequestMethod.GET, value = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<ApiDeckBuilder> deck(@PathVariable String id) throws Exception {
        log.debug("Deck builder user {} imports deck {}", ApiUtils.extractUserId(), id);
        ApiDeckBuilder deck = deckBuilderService.getDeck(id);
        if (deck == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(deck, HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/{type}/import", consumes = TEXT_PLAIN_VALUE, produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<ApiDeckBuilder> deck(@PathVariable ImportType type, @RequestBody String url) throws Exception {
        log.debug("Deck builder user {} imports {} {}", ApiUtils.extractUserId(), type, url);
        ApiDeckBuilder deck = deckBuilderService.importDeck(url, type);
        if (deck == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(deck, HttpStatus.OK);
        }
    }


    @RequestMapping(method = RequestMethod.POST, consumes = {MediaType.APPLICATION_JSON_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<ApiDeckBuilder> storeDeck(@RequestBody ApiDeckBuilder deckBuilder) {
        log.info("Deck builder user {} stores {} with id {}", ApiUtils.extractUserId(), deckBuilder.getName(), deckBuilder.getId());
        return new ResponseEntity<>(deckBuilderService.storeDeck(deckBuilder), HttpStatus.OK);

    }


    @RequestMapping(method = RequestMethod.DELETE, value = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<Boolean> deleteDeck(@PathVariable String id) {
        log.info("Deck builder user {} deletes {}", ApiUtils.extractUserId(), id);
        ApiDeckBuilder deck = deckBuilderService.getDeck(id);
        if (deck == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(deckBuilderService.deleteDeck(id), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/{id}/restore", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<Boolean> restoreDeck(@PathVariable String id) {
        log.info("Deck builder user {} restores {}", ApiUtils.extractUserId(), id);
        return new ResponseEntity<>(deckBuilderService.restoreDeck(id), HttpStatus.OK);
    }


}
