package com.vtesdecks.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.List;

@org.springframework.context.annotation.Configuration
public class CGGTypesenseConfiguration {
    @Value("${cgg.typesense.protocol:http}")
    private String protocol;
    @Value("${cgg.typesense.host:localhost}")
    private String host;
    @Value("${cgg.typesense.port:8108}")
    private String port;
    @Value("${cgg.typesense.apiKey:xxx}")
    private String apiKey;

    @Bean
    public Client typesenseClient() {
        List<Node> nodes = List.of(new Node(protocol, host, port));
        Configuration configuration = new Configuration(nodes, Duration.ofSeconds(10), apiKey);
        return new Client(configuration);
    }
}
