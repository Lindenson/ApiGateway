package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.core.context.MessageContext;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.DeliveryChannel;

import java.time.Duration;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DeliveryStage implements PipelineStage<MessageContext<Message>> {

    private final MessagesConfig messagesConfig;
    private final DeliveryChannel<Message> channel;
    private final PresenceManager presenceManager;
    private final IdempotencyManager<Message> idempotencyManager;

    private static Duration MIN_BACKOFF;
    private static Duration MAX_BACKOFF;
    private static int MAX_RETRIES;

    @PostConstruct
    void init() {
        MIN_BACKOFF = Duration.ofMillis(messagesConfig.channelRetry().minBackoffMs());
        MAX_BACKOFF = Duration.ofMillis(messagesConfig.channelRetry().maxBackoffMs());
        MAX_RETRIES = messagesConfig.channelRetry().maxRetries();
    }

    @Override
    public Uni<MessageContext<Message>> apply(MessageContext<Message> ctx) {
        return deliver(ctx)
                .invoke(ctx::setDelivered)
                .replaceWith(ctx)
                .onFailure().invoke(ctx::setError)
                .onFailure().recoverWithItem(ctx);
    }

    private Uni<Boolean> deliver(MessageContext<Message> ctx) {
        Uni<Boolean> deliveryResult = isDeliverable(ctx)
                .onItem().transformToUni(canDeliver ->
                        canDeliver ? channel.deliver(ctx.getPayload())
                                : Uni.createFrom().item(Boolean.FALSE));
        return messagesConfig.channelRetry().retry()
                ? applyRetryPolicy(deliveryResult)
                : deliveryResult;
    }

    private Uni<Boolean> isDeliverable(MessageContext<Message> ctx) {
        Message message = ctx.getPayload();
        return idempotencyManager.inProgress(message)
                .flatMap(progressing -> {
                    if (progressing) return Uni.createFrom().item(Boolean.FALSE);
                    else return presenceManager.isPresent(message.getRecipientId());
                });
    }


    private Uni<Boolean> applyRetryPolicy(Uni<Boolean> uni) {
        return uni.onFailure().retry().withBackOff(MIN_BACKOFF, MAX_BACKOFF).atMost(MAX_RETRIES);
    }
}