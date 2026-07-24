package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Minimum price of a card in the default currency, kept in sync with the price shown to users
 * (see LibraryFactory/CryptFactory) by the card caches on every refresh. Used to sort by price in SQL.
 */
@Entity
@Table(name = "card_min_price")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class CardMinPriceEntity {
    @Id
    @Column(name = "card_id")
    private Integer cardId;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}
