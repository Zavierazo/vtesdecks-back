package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiShoppingOptimizerService;
import com.vtesdecks.model.api.ApiShoppingOptimization;
import com.vtesdecks.model.api.ApiShoppingOptimizationRequest;
import com.vtesdecks.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api/1.0/shopping")
@Slf4j
public class ApiShoppingController {
    @Autowired
    private ApiShoppingOptimizerService shoppingOptimizerService;

    @RequestMapping(method = RequestMethod.POST, value = "/optimize", consumes = {
            MediaType.APPLICATION_JSON_VALUE
    }, produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<ApiShoppingOptimization> optimize(HttpServletRequest request, @RequestBody ApiShoppingOptimizationRequest optimizationRequest) {
        if (optimizationRequest == null || CollectionUtils.isEmpty(optimizationRequest.getCards())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            ApiShoppingOptimization result = shoppingOptimizerService.optimize(optimizationRequest.getCards(), Utils.getCurrencyCode(request));
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid shopping optimization request: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
