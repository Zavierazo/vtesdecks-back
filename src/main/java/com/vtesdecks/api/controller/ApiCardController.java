package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCardInfoService;
import com.vtesdecks.api.service.ApiCardService;
import com.vtesdecks.model.api.ApiBaseCard;
import com.vtesdecks.model.api.ApiCardInfo;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiRuling;
import com.vtesdecks.model.api.ApiShop;
import com.vtesdecks.model.api.ApiShopResult;
import com.vtesdecks.model.scanner.ScanRequest;
import com.vtesdecks.model.scanner.ScanResponse;
import com.vtesdecks.util.Utils;
import com.vtesdecks.util.VtesUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/api/1.0/cards")
@Slf4j
public class ApiCardController {
    @Autowired
    private ApiCardService apiCardService;
    @Autowired
    private ApiCardInfoService apiCardInfoService;


    @GetMapping(value = "/search", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiBaseCard>> searchCards(@RequestParam String query, @RequestParam(required = false) Double minScore, @RequestParam(required = false) Integer limit, @RequestParam(required = false) Set<String> fields) {
        List<ApiBaseCard> results = apiCardService.searchCards(query, minScore, limit, fields);
        return new ResponseEntity<>(results, HttpStatus.OK);
    }


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

    @GetMapping(value = "/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiBaseCard> getCard(HttpServletRequest request, @RequestParam(required = false, defaultValue = "en") String locale, @PathVariable Integer id) {
        ApiBaseCard card;
        if (VtesUtils.isCrypt(id)) {
            card = apiCardService.getCrypt(id, locale);
        } else {
            card = apiCardService.getLibrary(id, locale);
        }
        if (card == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(card, HttpStatus.OK);
        }
    }

    @GetMapping(value = "/{id}/info", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiCardInfo> getCardInfo(HttpServletRequest request, @RequestParam(required = false, defaultValue = "en") String locale, @PathVariable Integer id) {
        return new ResponseEntity<>(apiCardInfoService.getCardInfo(id, Utils.getCurrencyCode(request), locale), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}/rulings", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiRuling>> getRulings(@PathVariable Integer id) {
        return new ResponseEntity<>(apiCardInfoService.getRulings(id), HttpStatus.OK);
    }


    @GetMapping(value = "/{id}/shops", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiShop>> getCardShops(@PathVariable Integer id, @RequestParam(required = false, defaultValue = "en") String locale, @RequestParam(required = false, defaultValue = "false") Boolean showAll) {
        ApiShopResult result = apiCardService.getCardShops(id, locale, showAll);
        return new ResponseEntity<>(result.getShops(), HttpStatus.OK);
    }

    @PostMapping(value = "/scan", consumes = {
            MediaType.APPLICATION_JSON_VALUE
    }, produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ScanResponse> scan(@RequestBody ScanRequest request) {
        ScanResponse response = apiCardService.scan(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
