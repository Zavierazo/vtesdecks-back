package com.vtesdecks.model;

import com.vtesdecks.enums.AsyncAiStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsyncAiTask {
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

