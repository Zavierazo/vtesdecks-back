package com.vtesdecks.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vtesdecks.enums.AsyncAiStatus;
import com.vtesdecks.model.AsyncAiTask;
import com.vtesdecks.model.api.ApiAiAsyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAiService {
    private final AsyncAiWorker asyncAiWorker;

    private final Cache<String, AsyncAiTask> taskCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    /**
     * Creates a new async task and processes it in the background
     */
    public String createAsyncTask(ApiAiAsyncRequest request, Integer userId) {
        String taskId = UUID.randomUUID().toString();

        AsyncAiTask task = new AsyncAiTask();
        task.setTaskId(taskId);
        task.setSessionId(request.getSessionId());
        task.setQuestion(request.getQuestion());
        task.setUserId(userId);
        task.setStatus(AsyncAiStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskCache.put(taskId, task);

        // Process the task asynchronously using the worker
        asyncAiWorker.processTask(task, this);

        return task.getTaskId();
    }

    /**
     * Updates a task in the cache
     */
    public void updateTask(AsyncAiTask task) {
        taskCache.put(task.getTaskId(), task);
    }

    /**
     * Get the status of an async AI task
     */
    public AsyncAiTask getTaskStatus(String taskId) {
        return taskCache.getIfPresent(taskId);
    }
}

