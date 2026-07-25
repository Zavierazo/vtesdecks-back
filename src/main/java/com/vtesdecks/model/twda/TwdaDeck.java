package com.vtesdecks.model.twda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

/**
 * A tournament winning deck of the <a href="https://static.krcg.org/data/twda.json">KRCG TWDA</a>
 * archive. {@code id} matches the anchor id of the official {@code vekn.fr/decks/twd.htm} archive.
 * {@code player} is the tournament winner; {@code author} is only present when the deck was
 * created by someone else.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwdaDeck {
    private String id;
    private String event;
    private String eventLink;
    private String place;
    private LocalDate date;
    private String tournamentFormat;
    private Integer playersCount;
    private String player;
    private String score;
    private String name;
    private String author;
    private String comments;
    private TwdaCrypt crypt;
    private TwdaLibrary library;
}
