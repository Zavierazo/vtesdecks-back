package com.vtesdecks.jpa.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.enums.FeatureFlagType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flag")
@Data
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class FeatureFlagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "flag_key", nullable = false, unique = true)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private FeatureFlagType type;

    @Column(name = "value", columnDefinition = "json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode value;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}
