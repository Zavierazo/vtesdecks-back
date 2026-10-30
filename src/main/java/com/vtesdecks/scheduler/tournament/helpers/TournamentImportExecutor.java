package com.vtesdecks.scheduler.tournament.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Runs a bounded number of import workers and waits for every worker to finish. */
public final class TournamentImportExecutor {
    private TournamentImportExecutor() {
    }

    public static <T> void forEach(List<T> items, int parallelism, Consumer<T> importer)
            throws InterruptedException, ExecutionException {
        if (items.isEmpty()) {
            return;
        }
        int workers = Math.min(Math.max(1, parallelism), items.size());
        if (workers == 1) {
            items.forEach(importer);
            return;
        }
        AtomicInteger next = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(workers,
                Thread.ofPlatform().name("tournament-import-", 0).factory())) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                tasks.add(executor.submit(() -> {
                    int index;
                    while ((index = next.getAndIncrement()) < items.size()) {
                        importer.accept(items.get(index));
                    }
                }));
            }
            try {
                for (Future<?> task : tasks) {
                    task.get();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}
