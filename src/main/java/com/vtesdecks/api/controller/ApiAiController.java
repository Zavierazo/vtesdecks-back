package com.vtesdecks.api.controller;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.enums.AsyncAiStatus;
import com.vtesdecks.model.AsyncAiTask;
import com.vtesdecks.model.api.ApiAiAskRequest;
import com.vtesdecks.model.api.ApiAiAskResponse;
import com.vtesdecks.model.api.ApiAiAsyncRequest;
import com.vtesdecks.model.api.ApiAiAsyncResponse;
import com.vtesdecks.model.api.ApiAiAsyncStatusResponse;
import com.vtesdecks.service.AiService;
import com.vtesdecks.service.AsyncAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/1.0/ai")
@Slf4j
@RequiredArgsConstructor
public class ApiAiController {
    private final AiService aiService;
    private final AsyncAiService asyncAiService;


    @Deprecated
    @PostMapping(value = "/ask", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAskResponse ask(@RequestBody ApiAiAskRequest aiRequest) {
        log.warn("Synchronous AI ask endpoint is deprecated.");
        return aiService.ask(aiRequest, ApiUtils.extractUserId());
    }

    @PostMapping(value = "/ask/async", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAsyncResponse askAsync(@RequestBody ApiAiAsyncRequest asyncRequest) {
        ApiAiAsyncResponse response = new ApiAiAsyncResponse();

        try {
            if (asyncRequest == null) {
                response.setError("Invalid request");
                return response;
            }

            if (asyncRequest.getSessionId() == null || asyncRequest.getSessionId().trim().isEmpty()) {
                response.setError("Invalid request: sessionId is required");
                return response;
            }

            if (asyncRequest.getQuestion() == null || asyncRequest.getQuestion().trim().isEmpty()) {
                response.setError("Invalid request: question is required");
                return response;
            }

            Integer userId = ApiUtils.extractUserId();

            String taskId = asyncAiService.createAsyncTask(asyncRequest, userId);
            response.setTaskId(taskId);
            log.info("Created async AI task with taskId: {}", taskId);
        } catch (Exception e) {
            log.error("Error creating async AI task", e);
            response.setError("Error creating async task: " + e.getMessage());
        }
        return response;
    }

    @GetMapping(value = "/ask/async/{taskId}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAsyncStatusResponse getAsyncStatus(@PathVariable String taskId) {
        ApiAiAsyncStatusResponse response = new ApiAiAsyncStatusResponse();

        try {
            AsyncAiTask task = asyncAiService.getTaskStatus(taskId);

            if (task == null) {
                response.setStatus(AsyncAiStatus.ERROR);
                response.setError("Task not found or expired");
                return response;
            }

            response.setStatus(task.getStatus());
            if (task.getStatus() == AsyncAiStatus.COMPLETED) {
                response.setMessage(task.getResult());
            } else if (task.getStatus() == AsyncAiStatus.ERROR) {
                response.setError(task.getError());
            }

        } catch (Exception e) {
            log.error("Error getting async AI task status for taskId: {}", taskId, e);
            response.setStatus(AsyncAiStatus.ERROR);
            response.setError("Error getting task status: " + e.getMessage());
        }
        return response;
    }

}
