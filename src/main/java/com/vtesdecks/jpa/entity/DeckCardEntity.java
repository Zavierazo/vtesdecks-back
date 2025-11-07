package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deck_card")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class DeckCardEntity {
    @EmbeddedId
    private DeckCardId id;

    @Column(name = "number", nullable = false)
    private Integer number;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;

    @Data
    public static class DeckCardId {

        @Column(name = "deck_id", nullable = false)
        private String deckId;

        @Column(name = "id", nullable = false)
        private Integer cardId;
    }
}