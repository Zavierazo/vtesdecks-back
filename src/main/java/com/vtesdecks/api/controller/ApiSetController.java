package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiSetService;
import com.vtesdecks.model.api.ApiSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/api/1.0/sets")
@Slf4j
public class ApiSetController {

    @Autowired
    private ApiSetService apiSetService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> getSets(@RequestParam(required = false) Integer id, @RequestParam(required = false) String abbrev) {
        if (id == null && abbrev == null) {
            List<ApiSet> sets = apiSetService.getSets();
            return new ResponseEntity<>(sets, HttpStatus.OK);
        } else {
            ApiSet set = id == null ? apiSetService.getSet(abbrev) : apiSetService.getSet(id);
            if (set == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            } else {
                return new ResponseEntity<>(set, HttpStatus.OK);
            }
        }
    }

    @GetMapping(value = "/lastUpdate", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiSet> getCryptLastUpdate() {
        return new ResponseEntity<>(apiSetService.getLastUpdate(), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiSet> getSetById(@PathVariable Integer id) {
        ApiSet set = apiSetService.getSet(id);
        if (set == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(set, HttpStatus.OK);
        }
    }
}
