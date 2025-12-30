package com.vtesdecks.cache.redis.entity;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "DeckArchetype")
public class DeckArchetype {
    @Id
    private Integer id;
    private String name;
    private String icon;
    private String type;
    private String description;
    @Indexed
    private String deckId;
    private Boolean enabled;
    private Long deckCount;
    private Long tournamentCount;
    private Long tournament90Count;
    private Long tournament180Count;
    private Long tournament365Count;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
}
