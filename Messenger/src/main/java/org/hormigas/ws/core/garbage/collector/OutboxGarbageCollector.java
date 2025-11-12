package org.hormigas.ws.core.garbage.collector;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.garbage.GarbageCollector;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.ports.outbox.OutboxManager;
import org.hormigas.ws.ports.watermark.WatermarksRegistry;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxGarbageCollector implements GarbageCollector {

    public final int maxWatermarks;
    private final OutboxManager<Message> outboxManager;
    private final WatermarksRegistry watermarksRegistry;

    @Override
    public Uni<Long> collect() {
        return watermarksRegistry.getWatermarks(maxWatermarks)
                .onItem().ifNotNull().transformToUni(watermarks -> {
                    if (watermarks.isEmpty()) {
                        log.trace("No watermarks available for cleanup");
                        return Uni.createFrom().item(0L);
                    }

                    List<Uni<Long>> jobs = watermarks.stream()
                            .map(wm -> outboxManager.collectGarbage(msg ->
                                    msg.getServerTimestamp() <= wm.timestamp()
                                            && msg.getRecipientId().equals(wm.clientId())
                            )).toList();

                    return Multi.createFrom().iterable(jobs)
                            .onItem().transformToUniAndConcatenate(u -> u).collect().asList()
                            .onItem().transform(list -> list.stream().mapToLong(Long::longValue).sum());
                });
    }
}
