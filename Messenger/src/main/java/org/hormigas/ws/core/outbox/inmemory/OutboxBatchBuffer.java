package org.hormigas.ws.core.outbox.inmemory;

import io.smallrye.mutiny.Multi;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.domain.Message;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OutboxBatchBuffer {

    private static final int BATCH_SIZE = 1000;
    private static final Duration FLUSH_INTERVAL = Duration.ofMillis(500);

    private final List<Message> buffer = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();


    private final OutboxManagerInMemory outboxManager;

    public OutboxBatchBuffer(OutboxManagerInMemory outboxManager) {
        this.outboxManager = outboxManager;
        startAutoFlush();
    }

    public Message add(@Nullable Message msg) {
        if (msg == null) return null;
        buffer.add(msg);
        if (buffer.size() >= BATCH_SIZE) {
            flush();
        }
        return msg;
    }

    private synchronized void flush() {
        if (buffer.isEmpty()) return;

        List<Message> toRemove = new ArrayList<>(buffer);
        buffer.clear();

        Multi.createFrom().iterable(toRemove)
                .onItem().transformToUniAndConcatenate(outboxManager::removeFromOutbox)
                .subscribe().with(
                        count -> log.debug("Removed {} messages", count),
                        err -> log.error("Failed to remove batch", err)
                );
    }

    private void startAutoFlush() {
        scheduler.scheduleAtFixedRate(this::flush,
                FLUSH_INTERVAL.toMillis(),
                FLUSH_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }
}