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
@Table(name = "crypt")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class CryptEntity {
    @Id
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "aka")
    private String aka;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "clan", nullable = false)
    private String clan;

    @Column(name = "path")
    private String path;

    @Column(name = "adv")
    private Boolean adv;

    @Column(name = "`group`", nullable = false)
    private Integer group;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "disciplines", nullable = false)
    private String disciplines;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "`set`", nullable = false)
    private String set;

    @Column(name = "title")
    private String title;

    @Column(name = "banned")
    private String banned;

    @Column(name = "artist")
    private String artist;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}