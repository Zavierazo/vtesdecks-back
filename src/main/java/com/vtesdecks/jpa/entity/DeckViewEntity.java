package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deck_view")
@Data
@EqualsAndHashCode(exclude = {"modificationDate"})
public class DeckViewEntity {
    @EmbeddedId
    private DeckViewId id;

    @Column(name = "source")
    private String source;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeckViewId {

        @Column(name = "id", nullable = false)
        private String id;

        @Column(name = "deck_id", nullable = false)
        private String deckId;
    }
}