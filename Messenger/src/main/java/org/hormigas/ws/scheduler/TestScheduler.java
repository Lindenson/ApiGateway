package org.hormigas.ws.scheduler;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.domen.Status;
import org.hormigas.ws.repository.MessageRepository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class TestScheduler {

    @Inject
    MessageRepository messageRepository;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @WithTransaction
    @Scheduled(every = "1s")
    public Uni<Void> insertRandomClientMessage() {
        Message msg = new Message();
        msg.setId(UUID.randomUUID());
        msg.setClientId("cf72f28a-77c9-4eed-b591-beb612ec8307");
        msg.setContent("Hello Vlad from database - " + counter.incrementAndGet());
        msg.setSendAt(LocalDateTime.now());
        msg.setStatus(Status.PENDING);

        return messageRepository.persist(msg)
                .replaceWithVoid();
    }

    @WithTransaction
    @Scheduled(every = "1s")
    public Uni<Void> insertRandomMasterMessage() {
        Message msg = new Message();
        msg.setId(UUID.randomUUID());
        msg.setClientId("c71e006c-5d07-4787-8054-2eb8feb8deb9");
        msg.setContent("Hello Den from database - " + counter.incrementAndGet());
        msg.setSendAt(LocalDateTime.now());
        msg.setStatus(Status.PENDING);

        return messageRepository.persist(msg)
                .replaceWithVoid();
    }
}
