package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.SitemapService;
import com.vtesdecks.api.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiSitemapControllerTest {
    @Test
    void exposesSingleSitemapOrIndexAtExistingEndpointAndKeepsChildNamespace() throws Exception {
        SitemapService service = mock(SitemapService.class);
        var entries = List.of(new SitemapService.Entry("https://vtesdecks.com/", null));
        when(service.entries()).thenReturn(entries);
        when(service.sitemap(entries)).thenReturn("<urlset/>", "<sitemapindex/>");
        when(service.page(entries, 1)).thenReturn("<urlset/>");
        when(service.page(entries, 0)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApiSitemapController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/sitemap.xml")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/xml")).andExpect(content().encoding("UTF-8")).andExpect(content().xml("<urlset/>"));
        mvc.perform(get("/api/sitemap.xml")).andExpect(status().isOk()).andExpect(content().xml("<sitemapindex/>"));
        mvc.perform(get("/api/sitemap/1.xml")).andExpect(status().isOk()).andExpect(content().xml("<urlset/>"));
        mvc.perform(get("/api/sitemap/0.xml")).andExpect(status().isNotFound());
        verify(service, times(4)).entries();
    }
}
