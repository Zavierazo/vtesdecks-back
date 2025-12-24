package com.vtesdecks.cache.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "UserVisit")
public class UserVisit {
    @Id
    private String id;
    @Indexed
    private Integer userId;
    private LocalDate date;
}
