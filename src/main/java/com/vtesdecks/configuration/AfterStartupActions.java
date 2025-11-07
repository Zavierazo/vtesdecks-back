package com.vtesdecks.configuration;

import com.vtesdecks.service.AsyncAfterStartupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AfterStartupActions implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    private AsyncAfterStartupService asyncAfterStartupService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Starting background tasks...");
        asyncAfterStartupService.doAsyncAfterStartupTasks();
    }
}
