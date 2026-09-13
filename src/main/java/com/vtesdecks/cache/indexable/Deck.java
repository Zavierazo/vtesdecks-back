package com.vtesdecks.cache.indexable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.indexable.deck.Stats;
import com.vtesdecks.cache.indexable.deck.SummaryStats;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.entity.CardErrataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Full, request-independent deck. Built transiently and cached only in Redis. */
@Data
@EqualsAndHashCode(callSuper = true)
@RedisHash("Deck")
public class Deck extends DeckSummary {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TimeToLive
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private Long cacheTtl;
    private String url;
    private String source;
    private String description;
    private List<Card> crypt = new ArrayList<>();
    private List<Card> library = new ArrayList<>();
    @JsonIgnore
    private String extraJson;
    private List<CardErrataEntity> erratas;
    private Set<DeckWarning> warnings;

    @Override
    public JsonNode getExtra() {
        if (extraJson == null) {
            return null;
        }
        try {
            return JSON.readTree(extraJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid deck extras", e);
        }
    }

    @Override
    public void setExtra(JsonNode extra) {
        extraJson = extra == null ? null : extra.toString();
    }

    @Override
    public Stats getStats() {
        return (Stats) super.getStats();
    }

    @Override
    public void setStats(SummaryStats stats) {
        super.setStats(stats);
    }
}
