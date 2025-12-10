package com.vtesdecks.api.controller;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.model.api.ApiAiAskRequest;
import com.vtesdecks.model.api.ApiAiAskResponse;
import com.vtesdecks.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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


    @PostMapping(value = "/ask", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAskResponse ask(@RequestBody ApiAiAskRequest aiRequest) {
        return aiService.ask(aiRequest, ApiUtils.extractUserId());
    }

}
