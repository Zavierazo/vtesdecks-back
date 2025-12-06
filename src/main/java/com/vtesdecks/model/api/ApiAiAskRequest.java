package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiAiAskRequest {
    private String sessionId;
    private String question;
    private List<ApiAiMessage> chatHistory;
}
