package com.vtesdecks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAfterStartupService {
    private final AfterStartupService afterStartupService;

    @Async
    public void doAsyncAfterStartupTasks() {
        afterStartupService.executeAfterStartupTasks();
    }
}
