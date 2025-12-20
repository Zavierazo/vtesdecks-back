package com.vtesdecks.cache.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.Set;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "ProxyCardOption")
public class ProxyCardOption {
    @Id
    private Integer cardId;
    private String cardName;
    private Set<String> sets;
}
