package com.vtesdecks.cache.redis.entity;

import com.vtesdecks.model.api.ApiRuling;
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
@RedisHash(value = "CardRuling", timeToLive = 43200) // 12 hours
public class CardRuling {
    @Id
    private Integer id;
    private List<ApiRuling> rulings;
}
