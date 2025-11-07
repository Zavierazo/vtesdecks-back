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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "`set`")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class SetEntity {
    @Id
    private Integer id;

    @Column(name = "abbrev", nullable = false)
    private String abbrev;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "company", nullable = false)
    private String company;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creationDate;
    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false, insertable = false)
    private LocalDateTime modificationDate;
}