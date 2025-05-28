package org.hormigas.ws.scheduler;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.service.MessagePublisherImpl;
import org.hormigas.ws.service.MessagePersistenceService;
import org.hormigas.ws.service.api.MessagePublisher;

import java.util.Objects;


@ApplicationScoped
public class MessageFetchScheduler {

    @Inject
    MessagePersistenceService messagePersistenceService;

    @Inject
    MessagePublisher messagePublisher;

    @Scheduled(every = "${processing.messages.scheduler.time-interval-sec}s")
    public Uni<Void> pollMessagesForDispatch() {
        Log.debug("Fetching messages for dispatch");

        return messagePersistenceService.getNextBatchToSend()
                .onItem().invoke(messages -> {
                    if (Objects.nonNull(messages)) messages.forEach(msg -> messagePublisher.publish(msg));
                }).replaceWithVoid();
    }
}
