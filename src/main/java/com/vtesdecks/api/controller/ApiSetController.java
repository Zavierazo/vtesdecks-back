package com.vtesdecks.api.controller;

import java.util.List;

import com.vtesdecks.model.api.ApiDeck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.vtesdecks.api.service.ApiSetService;
import com.vtesdecks.model.api.ApiSet;
import lombok.extern.slf4j.Slf4j;

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

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiSet> getSetById(@RequestParam Integer id) {
        ApiSet set = apiSetService.getSet(id);
        if (set == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(set, HttpStatus.OK);
        }
    }
}
