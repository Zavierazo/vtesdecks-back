package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiStatisticsService;
import com.vtesdecks.model.ApiDeckType;
import com.vtesdecks.model.api.ApiHistoricStatistic;
import com.vtesdecks.model.api.ApiYearStatistic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/api/1.0/statistics")
@Slf4j
@RequiredArgsConstructor
public class ApiStatisticsController {
    private final ApiStatisticsService apiStatisticsService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiYearStatistic> comments(@RequestParam(name = "year") Integer year, @RequestParam(name = "type", required = false, defaultValue = "ALL") ApiDeckType type) {
        return new ResponseEntity<>(apiStatisticsService.getYearStatistic(type, year), HttpStatus.OK);
    }

    @GetMapping(value = "/tags", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<ApiHistoricStatistic>> tags(@RequestParam(name = "type", required = false, defaultValue = "ALL") ApiDeckType type) {
        return new ResponseEntity<>(apiStatisticsService.getHistoricTagStatistic(type), HttpStatus.OK);
    }


    @GetMapping(value = "/clans", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<ApiHistoricStatistic>> clans(@RequestParam(name = "type", required = false, defaultValue = "ALL") ApiDeckType type) {
        return new ResponseEntity<>(apiStatisticsService.getHistoricClanStatistic(type), HttpStatus.OK);
    }

    @GetMapping(value = "/disciplines", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<ApiHistoricStatistic>> disciplines(@RequestParam(name = "type", required = false, defaultValue = "ALL") ApiDeckType type) {
        return new ResponseEntity<>(apiStatisticsService.getHistoricDisciplineStatistic(type), HttpStatus.OK);
    }
}
