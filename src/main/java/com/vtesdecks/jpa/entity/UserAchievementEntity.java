package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_achievement",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "achievement_id", "occurrence_key"}
        )
)
@Data
public class UserAchievementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "achievement_id", nullable = false, length = 80)
    private String achievementId;

    @Column(nullable = false, length = 50)
    private String family;

    @Column(name = "tier_value", nullable = false)
    private Integer tierValue;

    @Column(name = "occurrence_key", nullable = false, length = 30)
    private String occurrenceKey = "";

    @Column(nullable = false)
    private Boolean historical = false;

    @CreationTimestamp
    @Column(name = "earned_date", nullable = false, updatable = false)
    private LocalDateTime earnedDate;
}
