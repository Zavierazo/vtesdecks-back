package com.vtesdecks.service;

import com.vtesdecks.cache.redis.entity.AiChatTask;
import com.vtesdecks.cache.redis.repositories.AiChatTaskRepository;
import com.vtesdecks.enums.AsyncAiStatus;
import com.vtesdecks.model.api.ApiAiAsyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAiService {
    private final AsyncAiWorker asyncAiWorker;
    private final AiChatTaskRepository aiChatTaskRepository;

    /**
     * Creates a new async task and processes it in the background
     */
    public String createAsyncTask(ApiAiAsyncRequest request, Integer userId) {
        String taskId = UUID.randomUUID().toString();

        AiChatTask task = new AiChatTask();
        task.setTaskId(taskId);
        task.setSessionId(request.getSessionId());
        task.setQuestion(request.getQuestion());
        task.setUserId(userId);
        task.setStatus(AsyncAiStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        aiChatTaskRepository.save(task);

        // Process the task asynchronously using the worker
        asyncAiWorker.processTask(task, this);

        return task.getTaskId();
    }

    /**
     * Updates a task in the cache
     */
    public void updateTask(AiChatTask task) {
        aiChatTaskRepository.save(task);
    }

    /**
     * Get the status of an async AI task
     */
    public AiChatTask getTaskStatus(String taskId) {
        return aiChatTaskRepository.findById(taskId).orElse(null);
    }
}

