package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCardService;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiShop;
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
@RequestMapping("/api/1.0/cards")
@Slf4j
public class ApiCardController {
    @Autowired
    private ApiCardService apiCardService;

    @GetMapping(value = "/crypt/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiCrypt> crypt(@PathVariable Integer id, @RequestParam(required = false) String locale) {
        ApiCrypt crypt = apiCardService.getCrypt(id, locale);
        if (crypt == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(crypt, HttpStatus.OK);
        }
    }

    @GetMapping(value = "/crypt", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiCrypt>> getAllCrypt(@RequestParam(required = false) String locale) {
        return new ResponseEntity<>(apiCardService.getAllCrypt(locale), HttpStatus.OK);
    }

    @GetMapping(value = "/crypt/lastUpdate", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiCrypt> getCryptLastUpdate() {
        return new ResponseEntity<>(apiCardService.getCryptLastUpdate(), HttpStatus.OK);
    }

    @GetMapping(value = "/library/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiLibrary> library(@PathVariable Integer id, @RequestParam(required = false) String locale) {
        ApiLibrary library = apiCardService.getLibrary(id, locale);
        if (library == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(library, HttpStatus.OK);
        }
    }

    @GetMapping(value = "/library", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiLibrary>> getAllLibrary(@RequestParam(required = false) String locale) {
        return new ResponseEntity<>(apiCardService.getAllLibrary(locale), HttpStatus.OK);
    }

    @GetMapping(value = "/library/lastUpdate", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiLibrary> getLibraryLastUpdate() {
        return new ResponseEntity<>(apiCardService.getLibraryLastUpdate(), HttpStatus.OK);
    }


    @GetMapping(value = "/{id}/shops", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiShop>> getCardShops(@PathVariable Integer id) {
        return new ResponseEntity<>(apiCardService.getCardShops(id), HttpStatus.OK);
    }


}
