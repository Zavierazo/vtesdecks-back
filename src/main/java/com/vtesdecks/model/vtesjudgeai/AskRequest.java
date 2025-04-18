package com.vtesdecks.model.vtesjudgeai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AskRequest {
    @JsonProperty("chat_history")
    private List<ChatMessage> chatHistory;
    private String question;
}
