package org.hormigas.ws.core.router;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.outbox.OutboxManager;
import org.hormigas.ws.core.sender.MessageSender;
import org.hormigas.ws.domain.MessagePayload;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class MessageRouterDefault implements MessageRouter {

    private final MessageSender<MessagePayload> messageSender;
    private final IdempotencyManager idempotencyManager;
    private final OutboxManager outboxManager;
    private final PolicyResolver policyResolver = new PolicyResolverDefault();

    @Override
    public Uni<Boolean> route(MessagePayload message) {

        RoutePolicy routePolicy = policyResolver.resolvePolicy(message);
        log.debug("Routing message: {}", message);
        log.debug("Resolved log policy: {}", routePolicy.toString());

        return switch (routePolicy) {
            case PERSISTENT_ACK -> outboxManager
                    .removeFromOutbox(message)
                    .onItem().transformToUni(idempotencyManager::removeMessage)
                    .replaceWith(Boolean.TRUE)
                    .onFailure().invoke(e -> log.error("Failed to process PERSISTENT_ACK message {}", message, e))
                    .onFailure().recoverWithItem(Boolean.FALSE);

            case CACHED_ACK -> idempotencyManager.removeMessage(message)
                    .replaceWith(Boolean.TRUE)
                    .onFailure().invoke(e -> log.error("Failed to process CACHED_ACK message {}", message, e))
                    .onFailure().recoverWithItem(Boolean.FALSE);

            case PERSISTENT_OUT -> outboxManager
                    .saveToOutbox(message)
                    .onItem().transformToUni(messageSender::send)
                    .onItem().transformToUni(idempotencyManager::addMessage)  // нужен короткий TTL
                    .replaceWith(Boolean.TRUE)
                    .onFailure().invoke(e -> log.error("Failed to process PERSISTENT_OUT message {}", message, e))
                    .onFailure().recoverWithItem(Boolean.FALSE);

            case CACHED_OUT -> messageSender
                    .send(message)
                    .onItem().transformToUni(idempotencyManager::addMessage)  // нужен короткий TTL
                    .replaceWith(Boolean.TRUE)
                    .onFailure().invoke(e -> log.error("Failed to process CACHED_OUT message {}", message, e))
                    .onFailure().recoverWithItem(Boolean.FALSE);

            case DIRECT_OUT -> messageSender
                    .send(message)
                    .replaceWith(Boolean.TRUE)
                    .onFailure().invoke(e -> log.error("Failed to process DIRECT_OUT message {}", message, e))
                    .onFailure().recoverWithItem(Boolean.FALSE);
        };
    }
}
