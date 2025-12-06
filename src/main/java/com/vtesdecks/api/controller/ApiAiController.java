package com.vtesdecks.api.controller;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.configuration.N8NConfiguration;
import com.vtesdecks.integration.N8NClient;
import com.vtesdecks.jpa.entity.UserAiAskEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAiAskRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAiAskRequest;
import com.vtesdecks.model.api.ApiAiAskResponse;
import com.vtesdecks.model.n8n.RAGRequest;
import com.vtesdecks.model.n8n.RAGResponse;
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

    private final UserRepository userRepository;
    private final UserAiAskRepository userAiAskRepository;
    private final N8NConfiguration n8NConfiguration;
    private final N8NClient n8NClient;


    @PostMapping(value = "/ask", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAskResponse ask(@RequestBody ApiAiAskRequest aiRequest) {
        ApiAiAskResponse aiResponse = new ApiAiAskResponse();
        if (aiRequest == null || aiRequest.getQuestion() == null || aiRequest.getSessionId() == null) {
            aiResponse.setMessage("Invalid request");
            return aiResponse;
        }

        Integer userId = ApiUtils.extractUserId();
        UserEntity userEntity = userId != null ? userRepository.findById(userId).orElse(null) : null;
        if (userEntity != null && Boolean.TRUE.equals(userEntity.getValidated())) {
            final String user = String.valueOf(userEntity.getId());
            if (Boolean.FALSE.equals(userEntity.getAdmin()) && userAiAskRepository.selectLastByUser(user) > 10) {
                aiResponse.setMessage("Quota exceeded. Please wait before asking another question.");
            } else {
                RAGRequest request = new RAGRequest();
                request.setSessionId(aiRequest.getSessionId());
                request.setChatInput(aiRequest.getQuestion());
                RAGResponse response = n8NClient.ask(n8NConfiguration.getVtesRagApiKey(), request);
                if (response != null) {
                    aiResponse.setMessage(response.getOutput());
                } else {
                    aiResponse.setMessage("We encountered an issue processing your request. Please try again. If the issue persists, please contact support and provide this session ID: " + aiRequest.getSessionId());
                }
                saveUserAiAsk(user);
            }
        } else {
            aiResponse.setMessage("You need to be logged in to ask questions");
        }
        return aiResponse;
    }

    private void saveUserAiAsk(String userId) {
        UserAiAskEntity userAiAsk = new UserAiAskEntity();
        userAiAsk.setUser(userId);
        userAiAskRepository.save(userAiAsk);
        userAiAskRepository.deleteOld();
    }

}
