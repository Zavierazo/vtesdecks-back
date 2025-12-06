package com.vtesdecks.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class N8NConfiguration {
    @Value("${n8n.vtes-rag.apiKey:xxx}")
    private String vtesRagApiKey;

}
