package org.hormigas.ws.core.scheduler;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Builder;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.domain.MessageOrigin;
import org.hormigas.ws.domain.MessagePayload;
import org.hormigas.ws.domain.MessageType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


@ApplicationScoped
@IfBuildProfile("prod")
public class TestScheduler {

    static final List<Clients> clients;

    static {
        clients = List.of(
                Clients.builder().clientId("cf72f28a-77c9-4eed-b591-beb612ec8307")
                        .message("Hello Vlad from database - ").build(),
                Clients.builder().clientId("c71e006c-5d07-4787-8054-2eb8feb8deb9")
                        .message("Hello Den from database - ").build()
        );
    }

    @Inject
    OutboxManager outboxManager;

    private static final AtomicInteger counter = new AtomicInteger(0);


    @Scheduled(every = "1s")
    public Uni<Void> insertRandomClientMessage() {
        return Multi.createFrom().range(0, 1000).onItem().transformToUniAndConcatenate(it -> {
            MessagePayload msg = MessagePayload.builder()
                    .id(UUID.randomUUID().toString())
                    .conversationId(UUID.randomUUID().toString())
                    .sender("System")
                    .recipient(clients.get(counter.get() % 2).clientId)
                    .type(MessageType.CHAT)
                    .origin(MessageOrigin.CLIENT)
                    .body("Hello-" + counter.incrementAndGet())
                    .metadata(Map.of())
                    .createdAt(LocalDateTime.now())
                    .build();

            return outboxManager.saveToOutbox(msg).replaceWithVoid();
        }).collect().asList().replaceWithVoid();
    }

    @Builder
    static class Clients {
        String clientId;
        String message;
    }
}
