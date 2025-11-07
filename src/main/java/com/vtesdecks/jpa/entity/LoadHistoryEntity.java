package com.vtesdecks.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "load_history")
@Data
@EqualsAndHashCode(exclude = {"modificationDate"})
public class LoadHistoryEntity {
    @Id
    private String script;

    @Column(name = "checksum", nullable = false)
    private String checksum;

    @Column(name = "execution_time", nullable = false)
    private String executionTime;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}