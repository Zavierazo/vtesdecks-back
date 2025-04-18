package com.vtesdecks.api.controller;

import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.db.UserAiAskMapper;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.db.model.DbUserAiAsk;
import com.vtesdecks.integration.VtesJudgeAiClient;
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
public class ApiUserAiController {

    private final UserMapper userMapper;
    private final UserAiAskMapper userAiAskMapper;
    private final VtesJudgeAiClient vtesJudgeAiClient;


    @PostMapping(value = "/ask", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiAiAskResponse ask(@RequestBody ApiAiAskRequest request) {
        ApiAiAskResponse response = new ApiAiAskResponse();
        DbUser user = userMapper.selectById(ApiUtils.extractUserId());
        if (user != null && user.isValidated() && user.isAdmin()) {
            Integer lastUserAiAsk = userAiAskMapper.selectLastByUser(String.valueOf(user.getId()));
            if (lastUserAiAsk != null && lastUserAiAsk > 10) {
                response.setMessage("Quota exceeded. Please wait before asking another question.");
            } else {
                AskRequest askRequest = new AskRequest();
                askRequest.setQuestion(request.getQuestion());
                askRequest.setChatHistory(mapChatHistory(request.getChatHistory()));
                AskResponse askResponse = vtesJudgeAiClient.getAmaranthDeck(askRequest);
                response.setMessage(askResponse.getAnswer());
                try {
                    saveUserAiAsk(user, request.getQuestion(), askResponse.getAnswer());
                } catch (Exception e) {
                    log.error("Unable to save user ai ask with user {} for question '{}' and answer '{}'", user.getId(), request.getQuestion(), askResponse.getAnswer(), e);
                }
            }
        } else {
            response.setMessage("You need to be logged in to ask questions");
        }
        return response;
    }

    private void saveUserAiAsk(DbUser user, String question, String answer) {
        DbUserAiAsk userAiAsk = new DbUserAiAsk();
        userAiAsk.setUser(String.valueOf(user.getId()));
        userAiAsk.setQuestion(question.substring(0, Math.min(question.length(), 2000)));
        userAiAsk.setAnswer(answer.substring(0, Math.min(answer.length(), 2000)));
        userAiAskMapper.insert(userAiAsk);
        userAiAskMapper.deleteOld();
    }

    private List<ChatMessage> mapChatHistory(List<ApiAiMessage> chatHistory) {
        return chatHistory.stream()
                .map(apiAiMessage -> new ChatMessage(apiAiMessage.getType().getValue(), apiAiMessage.getContent()))
                .toList();
    }

}
