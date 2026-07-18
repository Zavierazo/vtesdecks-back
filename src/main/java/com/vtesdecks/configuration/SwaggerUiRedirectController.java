package com.vtesdecks.configuration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Replaces springdoc's swagger-ui.html welcome redirect, which sends an absolute
 * Location (/api/swagger-ui/index.html) that the live proxy mangles into /api/api/...
 * A relative Location resolves against the URL the browser actually used, so it works
 * both locally (/api/swagger-ui.html) and behind the live proxy (/swagger-ui.html).
 */
@Controller
public class SwaggerUiRedirectController {

    @GetMapping("/api/swagger-ui.html")
    public void redirectToSwaggerUi(HttpServletResponse response) {
        // Set the header manually: sendRedirect() would resolve it to an absolute path
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, "swagger-ui/index.html");
    }
}
