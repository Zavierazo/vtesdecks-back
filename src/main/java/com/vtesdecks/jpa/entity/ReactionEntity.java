package com.vtesdecks.jpa.entity;

import com.vtesdecks.enums.ReactionTargetType;
import com.vtesdecks.enums.ReactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reaction")
@Data
@EqualsAndHashCode(exclude = {"creationDate"})
public class ReactionEntity {
    @EmbeddedId
    private ReactionId id;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class ReactionId {

        @Column(name = "user", nullable = false)
        private Integer user;

        @Enumerated(EnumType.STRING)
        @Column(name = "target_type", nullable = false)
        private ReactionTargetType targetType;

        @Column(name = "target_id", nullable = false)
        private String targetId;

        @Enumerated(EnumType.STRING)
        @Column(name = "reaction", nullable = false)
        private ReactionType reaction;
    }
}
