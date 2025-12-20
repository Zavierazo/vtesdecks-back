package com.vtesdecks.service;

import com.vtesdecks.cache.redis.entity.AiChatTask;
import com.vtesdecks.cache.redis.repositories.AiChatTaskRepository;
import com.vtesdecks.enums.AsyncAiStatus;
import com.vtesdecks.model.api.ApiAiAskRequest;
import com.vtesdecks.model.api.ApiAiAskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Worker separado para procesar tareas asíncronas
 * Este patrón evita la dependencia circular
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAiWorker {
    private final AiService aiService;
    private final AiChatTaskRepository aiChatTaskRepository;

    @Async("taskExecutor")
    @Transactional
    public void processTask(AiChatTask task) {
        try {
            // Update task status to PROCESSING
            task.setStatus(AsyncAiStatus.PROCESSING);
            task.setUpdatedAt(LocalDateTime.now());
            aiChatTaskRepository.save(task);

            // Call AI service
            ApiAiAskRequest aiRequest = new ApiAiAskRequest();
            aiRequest.setSessionId(task.getSessionId());
            aiRequest.setQuestion(task.getQuestion());
            ApiAiAskResponse aiResponse = aiService.ask(aiRequest, task.getUserId());

            // Task completed successfully
            task.setStatus(AsyncAiStatus.COMPLETED);
            task.setResult(aiResponse.getMessage());
            task.setUpdatedAt(LocalDateTime.now());

            log.info("Task {} completed successfully for user {}", task.getTaskId(), task.getUserId());
        } catch (Exception e) {
            log.error("Error processing async task {}", task.getTaskId(), e);
            task.setStatus(AsyncAiStatus.ERROR);
            task.setError("Error processing request: " + e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
        } finally {
            aiChatTaskRepository.save(task);
        }
    }
}

