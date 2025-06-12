package org.hormigas.ws.scheduler;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Builder;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.domen.Status;
import org.hormigas.ws.repository.MessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    MessageRepository messageRepository;

    private static final AtomicInteger counter = new AtomicInteger(0);

//    @WithTransaction
//    @Scheduled(every = "PT0.001S")
//    public Uni<Void> insertRandomClientMessage() {
//        return Multi.createFrom().range(0, 10000).onItem().transformToUniAndConcatenate(it -> {
//            Message msg = new Message();
//            msg.setId(UUID.randomUUID());
//            msg.setClientId(clients.get(0).clientId);
//            msg.setContent(clients.get(0).message + counter.incrementAndGet());
//            msg.setSendAt(LocalDateTime.now());
//            msg.setStatus(Status.PENDING);
//            return messageRepository.persist(msg).replaceWithVoid();
//        }).collect().asList().replaceWithVoid();
//    }
//
//    @WithTransaction
//    @Scheduled(every = "PT0.001S")
//    public Uni<Void> insertRandomMasterMessage() {
//
//        return Multi.createFrom().range(0, 10000).onItem().transformToUniAndConcatenate(it -> {
//            Message msg = new Message();
//            msg.setId(UUID.randomUUID());
//            msg.setClientId(clients.get(1).clientId);
//            msg.setContent(clients.get(1).message + counter.incrementAndGet());
//            msg.setSendAt(LocalDateTime.now());
//            msg.setStatus(Status.PENDING);
//            return messageRepository.persist(msg).replaceWithVoid();
//        }).collect().asList().replaceWithVoid();
//    }

    @Builder
    static class Clients {
        String clientId;
        String message;
    }
}
