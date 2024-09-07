package com.vtesdecks.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.model.api.ApiChangelog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Controller
@RequestMapping("/api/1.0/changelog")
@Slf4j
public class ApiChangelogController {
    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    public ApiChangelogController() {
        this.restTemplate = new RestTemplate();
    }

    @RequestMapping(method = RequestMethod.GET, produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiChangelog>> changelog() throws JsonProcessingException {
        ResponseEntity<String> response = restTemplate.getForEntity("https://raw.githubusercontent.com/Zavierazo/vtesdecks-front/main/src/assets/changelog.json", String.class);
        List<ApiChangelog> changelog = objectMapper.readValue(response.getBody(), new TypeReference<List<ApiChangelog>>() {
        });
        return new ResponseEntity<>(changelog, HttpStatus.OK);
    }
}
