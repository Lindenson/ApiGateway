package org.hormigas.ws.scheduler;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@ApplicationScoped
@IfBuildProfile("prod")
public class TestScheduler {

    static final List<Clients> clients;
    private final AtomicInteger published = new AtomicInteger(0);

    static {
        clients = List.of(
                Clients.builder().clientId("cf72f28a-77c9-4eed-b591-beb612ec8307")
                        .message("Hello Vlad from database - ").build(),
                Clients.builder().clientId("c71e006c-5d07-4787-8054-2eb8feb8deb9")
                        .message("Hello Den from database - ").build()
        );
    }

    @Inject
    OutboxManager<Message> outboxManager;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @Scheduled(every = "1s")
    public Uni<Void> insertRandomClientMessage() {
        return Multi.createFrom().range(0, 1000).onItem().transformToUniAndConcatenate(it -> {
            Message msg = Message.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(UUID.randomUUID().toString())
                    .recipientId(clients.get(counter.get() % 2).clientId)
                    .senderId("server")
                    .type(MessageType.CHAT_OUT)
                    .payload(new Message.Payload("text", "Hello-" + counter.incrementAndGet()))
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
