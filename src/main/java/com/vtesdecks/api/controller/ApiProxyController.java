package com.vtesdecks.api.controller;

import com.itextpdf.text.DocumentException;
import com.vtesdecks.api.service.ApiProxyService;
import com.vtesdecks.model.api.ApiProxy;
import com.vtesdecks.model.api.ApiProxyCardOption;
import com.vtesdecks.service.ProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/api/1.0/proxy")
@Slf4j
public class ApiProxyController {

    @Autowired
    private ApiProxyService proxyService;

    @RequestMapping(method = RequestMethod.POST, produces = {
            MediaType.APPLICATION_PDF_VALUE
    })
    @ResponseBody
    public ResponseEntity<byte[]> generateProxiesDocument(@RequestBody ApiProxy apiProxy) {
        byte[] documentData = new byte[0];
        HttpStatus status = HttpStatus.OK;

        try {
            documentData = proxyService.generatePDF(apiProxy.getCards());
        } catch (DocumentException | IOException e) {
            log.error("Error generating proxies file: {0}", e);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return new ResponseEntity<>(documentData, status);
    }

    @RequestMapping(
            value = "/options/{id}",
            method = RequestMethod.GET, produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ResponseEntity<List<ApiProxyCardOption>> getProxyOptions(@PathVariable Integer id){
        return new ResponseEntity<>(proxyService.getProxyOptions(id), HttpStatus.OK);
    }
}