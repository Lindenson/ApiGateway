package org.hormigas.ws.core.collector;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.watermark.WatermarksRegistry;
import org.hormigas.ws.domain.Message;

import java.util.List;

@Slf4j
@ApplicationScoped
public class OutboxGarbageCollector {

    public static final int MAX_WATERMARKS = 100;

    @Inject
    OutboxManager<Message> outboxManager;

    @Inject
    WatermarksRegistry watermarksRegistry;

    @Scheduled(every = "10s")
    public void run() {
        watermarksRegistry.getWatermarks(MAX_WATERMARKS)
                .onItem().transformToUni(watermarks -> {
                    List<Uni<Long>> jobs = watermarks.stream()
                            .map(wm -> outboxManager.collectGarbage(msg ->
                                    msg.getServerTimestamp() <= wm.timestamp()
                                    && msg.getRecipientId().equals(wm.clientId())
                            ))
                            .toList();

                    return Multi.createFrom().iterable(jobs)
                            .onItem().transformToUniAndConcatenate(u -> u)
                            .collect().asList()
                            .onItem().transform(list -> list.stream().mapToLong(Long::longValue).sum());
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        totalCollected -> log.debug("Garbage collection finished, total collected: {}", totalCollected),
                        err -> log.error("Garbage collection failed", err)
                );
    }
}
