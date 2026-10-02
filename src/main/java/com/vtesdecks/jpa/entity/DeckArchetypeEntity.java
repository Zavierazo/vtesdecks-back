package com.vtesdecks.jpa.entity;

import com.vtesdecks.jpa.entity.converter.ArchetypeCardRequirementsConverter;
import com.vtesdecks.model.ArchetypeCardRequirement;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "deck_archetype")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"creationDate", "modificationDate"})
public class DeckArchetypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "icon")
    private String icon;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "deck_id")
    private String deckId;

    @Column(name = "secondary_deck_id")
    private String secondaryDeckId;

    @Convert(converter = ArchetypeCardRequirementsConverter.class)
    @Column(name = "card_requirements", columnDefinition = "json")
    @Builder.Default
    private List<ArchetypeCardRequirement> cardRequirements = List.of();


    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @CreationTimestamp
    @Column(name = "creation_date", nullable = false)
    private LocalDateTime creationDate;

    @UpdateTimestamp
    @Column(name = "modification_date", nullable = false)
    private LocalDateTime modificationDate;
}

