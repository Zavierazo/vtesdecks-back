package com.vtesdecks.cache.redis.entity;

import com.vtesdecks.enums.AsyncAiStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

@Data
@RedisHash(value = "AiChatTask", timeToLive = 1800)// 30 minutes
public class AiChatTask {
    @Id
    private String taskId;
    private String sessionId;
    private String question;
    private Integer userId;
    private AsyncAiStatus status;
    private String result;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

