package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiSitemapController {
    private final SitemapService sitemapService;

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public String sitemap() {
        return sitemapService.sitemap(sitemapService.entries());
    }

    @GetMapping(value = "/sitemap/{page}.xml", produces = "application/xml;charset=UTF-8")
    public String sitemapPage(@PathVariable int page) {
        return sitemapService.page(sitemapService.entries(), page);
    }
}
