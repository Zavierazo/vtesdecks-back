package com.vtesdecks.service;

import com.google.common.collect.Lists;
import com.itextpdf.text.DocumentException;
import com.vtesdecks.model.api.ApiProxyCard;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Disabled
class ProxyServiceTest {

    @Test
    public void generateSinglePageProxies() throws IOException, DocumentException {
        ProxyService proxyService = new ProxyService();
        List<ApiProxyCard> cards = Lists.newArrayList(
                ApiProxyCard.builder().cardId(100001).setAbbrev("Jyhad").amount(2).build(),
                ApiProxyCard.builder().cardId(100002).setAbbrev("ek").amount(1).build(),
                ApiProxyCard.builder().cardId(201205).setAbbrev("bsc").amount(3).build(),
                ApiProxyCard.builder().cardId(200002).setAbbrev("third").amount(3).build()
        );

        byte[] documentBytes = proxyService.generatePDF(cards, new HashMap<>());

        File outputFile = new File("D:/tmp/vtes-proxy-singlePage.pdf");
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(documentBytes);
        }
    }

    @Test
    public void generateMultiPageProxies() throws IOException, DocumentException {
        ProxyService proxyService = new ProxyService();
        List<ApiProxyCard> cards = Lists.newArrayList(
                ApiProxyCard.builder().cardId(100001).setAbbrev("Jyhad").amount(4).build(),
                ApiProxyCard.builder().cardId(100002).setAbbrev("ek").amount(2).build(),
                ApiProxyCard.builder().cardId(201205).setAbbrev("bsc").amount(5).build(),
                ApiProxyCard.builder().cardId(200002).setAbbrev("third").amount(4).build(),
                ApiProxyCard.builder().cardId(200005).setAbbrev("lotn").amount(5).build()
        );

        byte[] documentBytes = proxyService.generatePDF(cards, new HashMap<>());

        File outputFile = new File("D:/tmp/vtes-proxy-multiPage.pdf");
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(documentBytes);
        }
    }

    @Test
    public void prova() {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity rs = restTemplate.exchange("https://cdn.vtesdecks.com/img/cards/sets/anarchs/300299.jpg", HttpMethod.HEAD, null, String.class);
        int a = 1;
    }

}