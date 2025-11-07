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
@Table(name = "crypt_i18n")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class CryptI18nEntity {
    @EmbeddedId
    private CryptI18nId id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "text")
    private String text;

    @Column(name = "image")
    private String image;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;

    @Data
    public static class CryptI18nId {

        @Column(name = "id", nullable = false)
        private Integer cardId;

        @Column(name = "locale", nullable = false)
        private String locale;
    }
}