package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "library")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class LibraryEntity {
    @Id
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "aka")
    private String aka;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "clan")
    private String clan;

    @Column(name = "path")
    private String path;

    @Column(name = "discipline")
    private String discipline;

    @Column(name = "pool_cost")
    private Integer poolCost;

    @Column(name = "blood_cost")
    private Integer bloodCost;

    @Column(name = "conviction_cost")
    private Integer convictionCost;

    @Column(name = "burn")
    private Boolean burn;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "flavor")
    private String flavor;

    @Column(name = "`set`")
    private String set;

    @Column(name = "requirement")
    private String requirement;

    @Column(name = "banned")
    private String banned;

    @Column(name = "artist")
    private String artist;

    @Column(name = "capacity")
    private String capacity;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}