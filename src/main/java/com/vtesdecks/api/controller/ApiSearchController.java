package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiSearchService;
import com.vtesdecks.model.api.ApiSearchResponse;
import com.vtesdecks.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/1.0/search")
@Slf4j
@RequiredArgsConstructor
public class ApiSearchController {
    private final ApiSearchService apiSearchService;

    @GetMapping(produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    public ApiSearchResponse search(HttpServletRequest request, @RequestParam(name = "query") String query) {
        return apiSearchService.search(query, Utils.getCurrencyCode(request));
    }

}