package com.vtesdecks.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "collection_binder")
@Data
public class CollectionBinder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "collection_id", nullable = false)
    private Integer collectionId;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(length = 100)
    private String icon;

    @Column(name = "public_visibility", nullable = false)
    private boolean publicVisibility = false;

    @Column(name = "public_hash")
    private String publicHash;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime modificationDate;
}