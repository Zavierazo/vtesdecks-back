package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_i18n_image")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class CardI18nImageEntity {
    @EmbeddedId
    private CardI18nImageId id;

    @Column(name = "image", nullable = false)
    private String image;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false)
    private LocalDateTime modificationDate;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardI18nImageId implements Serializable {
        @Column(name = "card_id", nullable = false)
        private Integer cardId;

        @Column(name = "locale", nullable = false, length = 2)
        private String locale;
    }
}
