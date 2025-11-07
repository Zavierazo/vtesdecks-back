package com.vtesdecks.api.controller;

import com.vtesdecks.jpa.entity.LimitedFormatEntity;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.model.limitedformat.LimitedFormatPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/1.0/limitedFormats")
@Slf4j
@RequiredArgsConstructor
public class ApiLimitedFormatController {
    private final LimitedFormatRepository limitedFormatRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<LimitedFormatPayload>> limitedFormat() {
        return new ResponseEntity<>(limitedFormatRepository.findAll().stream().map(LimitedFormatEntity::getFormat).toList(), HttpStatus.OK);
    }


}
