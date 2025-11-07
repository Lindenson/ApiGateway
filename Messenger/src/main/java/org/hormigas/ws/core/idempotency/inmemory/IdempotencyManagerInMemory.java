package org.hormigas.ws.core.idempotency.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;

import static org.hormigas.ws.core.router.stage.StageStatus.FAILED;
import static org.hormigas.ws.core.router.stage.StageStatus.SUCCESS;


@Slf4j
@ApplicationScoped
public class IdempotencyManagerInMemory implements IdempotencyManager<Message> {

    private final int MAX_IDEM_SIZE = 5000;

    private enum Status {SENT, ACK}

    private final ConcurrentInsertionOrderMap<String, Status> messages =
            new ConcurrentInsertionOrderMap<>(MAX_IDEM_SIZE, e -> e == Status.ACK);


    @Override
    public Uni<StageStatus> addMessage(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Adding message {}", message);
        return Uni.createFrom().item(messages.put(message.getMessageId(), Status.SENT))
                .replaceWith(SUCCESS);
    }

    @Override
    public Uni<StageStatus> removeMessage(@Nullable Message message) {
        if (message == null || message.getCorrelationId() == null) return Uni.createFrom().item(FAILED);

        log.debug("Removing message {}", message);
        return Uni.createFrom().item(messages.replace(message.getCorrelationId(), Status.ACK))
                .replaceWith(SUCCESS);
    }

    @Override
    public Uni<Boolean> inProgress(@Nullable Message message) {
        if (message == null || message.getMessageId() == null) {
            log.error("Message is null or empty {}", message);
            return Uni.createFrom().item(Boolean.FALSE);
        }

        log.debug("Checking message {}", message);
        if (messages.size() > MAX_IDEM_SIZE + 1) log.warn("TOO MUCH IDEM SIZE: {}", messages.size());
        return Uni.createFrom().item(messages.get(message.getMessageId()) != null)
                .onItem().invoke(found -> {
                    if (found) log.debug("Double sent {}", message.getMessageId());
                });
    }
}
