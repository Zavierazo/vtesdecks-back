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
@Table(name = "deck_user")
@Data
@EqualsAndHashCode(exclude = {"modificationDate"})
public class DeckUserEntity {
    @EmbeddedId
    private DeckUserId id;

    @Column(name = "rate")
    private Integer rate;

    @Column(name = "favorite", nullable = false)
    private Boolean favorite;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeckUserId {

        @Column(name = "user", nullable = false)
        private Integer user;

        @Column(name = "deck_id", nullable = false)
        private String deckId;
    }
}