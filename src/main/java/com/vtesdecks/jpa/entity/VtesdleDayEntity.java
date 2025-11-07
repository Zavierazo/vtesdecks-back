package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "vtesdle_day")
@Data
public class VtesdleDayEntity {
    @Id
    private LocalDate day;

    @Column(name = "card_id", nullable = false)
    private Integer cardId;
}