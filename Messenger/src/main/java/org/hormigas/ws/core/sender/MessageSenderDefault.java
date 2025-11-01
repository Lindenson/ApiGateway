package org.hormigas.ws.core.services.sender;

import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.domain.MessagePayload;
import org.hormigas.ws.core.services.idempotency.IdempotencyManager;
import org.hormigas.ws.core.services.presence.PresenceManager;
import org.hormigas.ws.ports.channel.DeliveryChannel;

import java.time.Duration;

@Slf4j
public class MessageSenderDefault implements MessageSender {

    PresenceManager presenceManager;
    IdempotencyManager idempotencyManager;
    DeliveryChannel deliveryChannel;

    @Override
    public Uni<MessagePayload> send(MessagePayload message) {

        Uni<Boolean> presenceUni = presenceManager.isPresent(message.recipient());
        Uni<Boolean> idempotentUni = idempotencyManager.inProgress(message);

        return Uni.combine().all().unis(presenceUni, idempotentUni).asTuple()
                .onItem().transformToUni(tuple -> {
                    boolean isPresent = tuple.getItem1();
                    boolean inProgress = tuple.getItem2();

                    if (!isPresent || inProgress) {
                        if (!isPresent) log.warn("Client {} is offline, message {} queued", message.recipient(), message.id());
                        else log.warn("Message {} has already been sent to client {}", message.id(), message.recipient());
                        return Uni.createFrom().failure(new IllegalStateException("Recipient offline"));
                    }

                    return deliveryChannel.deliver(message)
                            .onItem().invoke(() ->
                                    log.info("Message {} delivered to {}", message.id(), message.recipient()))
                            .onFailure().retry()
                            .withBackOff(Duration.ofMillis(200), Duration.ofSeconds(3))
                            .atMost(3)
                            .onFailure().invoke(err ->
                                    log.error("Failed to deliver message {}: {}", message.id(), err.getMessage()))
                            .replaceWith(message);
                });
    }
}