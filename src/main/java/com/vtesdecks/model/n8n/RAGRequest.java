package com.vtesdecks.model.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RAGRequest {
    private String sessionId;
    private String chatInput;
}
