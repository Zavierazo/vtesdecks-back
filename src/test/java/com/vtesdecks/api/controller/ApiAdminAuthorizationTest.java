package com.vtesdecks.api.controller;

import com.vtesdecks.api.GlobalExceptionHandler;
import com.vtesdecks.api.service.ApiAdminSchedulerService;
import com.vtesdecks.api.service.ApiAdminUserService;
import com.vtesdecks.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiAdminAuthorizationTest {
    private AnnotationConfigApplicationContext context;
    private ApiAdminSchedulerService schedulerService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(AuthorizationConfiguration.class);
        schedulerService = context.getBean(ApiAdminSchedulerService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ApiAdminController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void anonymousAdminRequestsReturnForbiddenWithoutRunningSchedulers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertAdminRequestsForbidden();
    }

    @Test
    void nonAdminRequestsReturnForbiddenWithoutRunningSchedulers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", null, List.of(new SimpleGrantedAuthority("USER"))));

        assertAdminRequestsForbidden();
    }

    @Test
    void adminCanStillListSchedulers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", null, List.of(new SimpleGrantedAuthority("ADMIN"))));

        mvc.perform(get("/api/1.0/admin/schedulers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(schedulerService).getAll();
    }

    private void assertAdminRequestsForbidden() throws Exception {
        mvc.perform(get("/api/1.0/admin/schedulers"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Access Denied"));
        mvc.perform(post("/api/1.0/admin/schedulers/deck-index"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Access Denied"));
        verifyNoInteractions(schedulerService);
    }

    @Configuration
    @EnableMethodSecurity(securedEnabled = true)
    static class AuthorizationConfiguration {
        @Bean
        ApiAdminSchedulerService schedulerService() {
            return mock(ApiAdminSchedulerService.class);
        }

        @Bean
        ApiAdminController adminController(ApiAdminSchedulerService schedulerService) {
            return new ApiAdminController(mock(ApiAdminUserService.class), schedulerService, mock(FeatureFlagService.class));
        }
    }
}
