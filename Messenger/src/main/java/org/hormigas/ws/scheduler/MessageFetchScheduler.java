package org.hormigas.ws.scheduler;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.service.MessagePublisher;
import org.hormigas.ws.service.MessageService;


@ApplicationScoped
public class MessageFetchScheduler {

    @Inject
    MessageService messageService;

    @Inject
    MessagePublisher messagePublisher;

    @Scheduled(every = "10s")
    public Uni<Void> pollMessagesForDispatch() {
        Log.debug("Fetching messages for dispatch");

        return messageService.getNextBatchToSend()
                .onItem().invoke(messages -> {
                    messages.forEach(msg -> messagePublisher.publish(msg));
                }).replaceWithVoid();
    }
}
