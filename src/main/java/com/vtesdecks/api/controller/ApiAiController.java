package com.vtesdecks.api.controller;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.integration.VtesJudgeAiClient;
import com.vtesdecks.jpa.entity.UserAiAskEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserAiAskRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAiAskRequest;
import com.vtesdecks.model.api.ApiAiAskResponse;
import com.vtesdecks.model.api.ApiAiMessage;
import com.vtesdecks.model.vtesjudgeai.AskRequest;
import com.vtesdecks.model.vtesjudgeai.AskResponse;
import com.vtesdecks.model.vtesjudgeai.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/ai")
@Slf4j
@RequiredArgsConstructor
public class ApiAiController {

    private final UserRepository userRepository;
    private final UserAiAskRepository userAiAskRepository;
    private final VtesJudgeAiClient vtesJudgeAiClient;


    @PostMapping(value = "/ask", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAskResponse ask(@RequestBody ApiAiAskRequest request) {
        ApiAiAskResponse response = new ApiAiAskResponse();
        UserEntity user = userRepository.findById(ApiUtils.extractUserId()).orElse(null);
        final String userId = user != null ? String.valueOf(user.getId()) : null;
        if (userId != null && user.getValidated() != null && user.getValidated()) {
            if ((user.getAdmin() == null || !user.getAdmin()) && userAiAskRepository.selectLastByUser(userId) > 10) {
                response.setMessage("Quota exceeded. Please wait before asking another question.");
            } else {
                AskRequest askRequest = new AskRequest();
                askRequest.setQuestion(request.getQuestion());
                askRequest.setChatHistory(mapChatHistory(request.getChatHistory()));
                AskResponse askResponse = vtesJudgeAiClient.getAmaranthDeck(askRequest);
                response.setMessage(askResponse.getAnswer());
                try {
                    saveUserAiAsk(userId, request.getQuestion(), askResponse.getAnswer());
                } catch (Exception e) {
                    log.error("Unable to save user ai ask with user {} for question '{}' and answer '{}'", userId, request.getQuestion(), askResponse.getAnswer(), e);
                }
            }
        } else {
            response.setMessage("You need to be logged in to ask questions");
        }
        return response;
    }

    private void saveUserAiAsk(String userId, String question, String answer) {
        UserAiAskEntity userAiAsk = new UserAiAskEntity();
        userAiAsk.setUser(userId);
        userAiAsk.setQuestion(question.substring(0, Math.min(question.length(), 2000)));
        userAiAsk.setAnswer(answer);
        userAiAskRepository.save(userAiAsk);
        //userAiAskRepository.deleteOld();
    }

    private List<ChatMessage> mapChatHistory(List<ApiAiMessage> chatHistory) {
        return chatHistory.stream()
                .map(apiAiMessage -> new ChatMessage(apiAiMessage.getType().getValue(), apiAiMessage.getContent()))
                .toList();
    }

}
