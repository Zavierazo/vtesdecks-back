package com.vtesdecks.jpa.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.converter.JsonNodeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deck")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class DeckEntity {
    @Id
    private String id;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private DeckType type;

    @Column(name = "user")
    private Integer user;

    @Column(name = "tournament")
    private String tournament;

    @Column(name = "players")
    private Integer players;

    @Column(name = "year")
    private Integer year;

    @Column(name = "author")
    private String author;

    @Column(name = "url")
    private String url;

    @Column(name = "source")
    private String source;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "`set`")
    private String set;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "extra", columnDefinition = "json")
    private JsonNode extra;

    @Column(name = "views")
    @Builder.Default
    private Long views = 0L;

    @Column(name = "verified")
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "published")
    @Builder.Default
    private Boolean published = true;

    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "collection")
    @Builder.Default
    private Boolean collection = false;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "deck_archetype_id")
    private Integer deckArchetypeId;

    @Column(name = "creation_date", nullable = false)
    private LocalDateTime creationDate;
    
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false)
    private LocalDateTime modificationDate;
}