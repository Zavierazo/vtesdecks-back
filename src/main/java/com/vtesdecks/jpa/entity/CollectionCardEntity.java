package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "collection_card")
@Data
public class CollectionCardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "collection_id", nullable = false)
    private Integer collectionId;

    @Column(name = "card_id", nullable = false)
    private Integer cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", referencedColumnName = "id", insertable = false, updatable = false)
    private CryptEntity crypt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", referencedColumnName = "id", insertable = false, updatable = false)
    private LibraryEntity library;

    @Column(name = "`set`")
    private String set;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "binder_id")
    private Integer binderId;

    @Column(name = "`condition`", length = 2)
    private String condition;

    @Column(length = 2)
    private String language;

    @Lob
    private String notes;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}