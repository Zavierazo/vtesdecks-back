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
@Table(name = "collection_card_history")
@Data
@EqualsAndHashCode(exclude = {"creationDate"})
public class CollectionCardHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "action", nullable = false, columnDefinition = "TINYINT")
    private DeckCardAction action;

    @Column(name = "collection_id", nullable = false)
    private Integer collectionId;

    @Column(name = "card_id", nullable = false)
    private Integer cardId;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "`set`")
    private String set;

    @Column(name = "`condition`", length = 2)
    private String condition;

    @Column(length = 2)
    private String language;

    @Column(name = "binder_id")
    private Integer binderId;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
}
