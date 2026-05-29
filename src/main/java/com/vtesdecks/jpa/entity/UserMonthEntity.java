package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_month")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMonthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** Stored as the 1st day of the month (e.g. 2026-05-01) */
    @Column(name = "month_date", nullable = false)
    private LocalDate monthDate;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Column(name = "score", nullable = false)
    private Long score;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
}


