package com.vtesdecks.cache.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "DeckTags", timeToLive = 3600) // 1 hour
public class DeckTags {
    public static final String CACHE_ID = "all";

    @Id
    private String id;
    private List<String> tags;
}
