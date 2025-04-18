package com.vtesdecks.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiAiMessage {
    public enum MessageType {
        HUMAN("human"),
        AI("ai");
        
        @Getter
        private String value;

        MessageType(String value) {
            this.value = value;
        }
    }

    private MessageType type;
    private String content;
}
