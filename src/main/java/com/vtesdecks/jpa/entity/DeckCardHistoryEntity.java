package com.vtesdecks.jpa.entity;

import com.vtesdecks.enums.DeckCardAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deck_card_history")
@Data
@EqualsAndHashCode(exclude = {"creationDate"})
public class DeckCardHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "action", nullable = false, columnDefinition = "TINYINT")
    private DeckCardAction action;

    @Column(name = "deck_id", nullable = false)
    private String deckId;

    @Column(name = "card_id", nullable = false)
    private Integer cardId;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "tag")
    private Integer tag;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;


}
