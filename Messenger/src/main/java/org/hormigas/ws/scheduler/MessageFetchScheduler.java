package org.hormigas.ws.scheduler;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.publisher.api.PublisherWithBackPressure;
import org.hormigas.ws.service.ClientMessagePublisher;
import org.hormigas.ws.service.api.MessagePersistence;


@ApplicationScoped
public class MessageFetchScheduler {

    @Inject
    MessagePersistence messagePersistence;

    @Inject
    ClientMessagePublisher messagePublisher;

    @Scheduled(every = "${processing.messages.scheduler.time-interval-sec}s")
    //@Scheduled(every = "PT0.01S")
    public Uni<Void> pollMessagesForDispatch() {
        Log.debug("Fetching messages for dispatch");
        if (messagePublisher.queueIsNotEmpty()) {
            Log.warn("Queue is not empty. We don't fetch messages");
            return Uni.createFrom().voidItem();
        }

        return messagePersistence.getNextBatchToSend()
                .chain(messages -> Uni.createFrom().item(messages)
                        .invoke(msgs -> msgs.forEach(messagePublisher::publish))).replaceWithVoid();
    }
}
