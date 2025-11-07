package org.hormigas.ws.core.router.stage.stages;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.core.idempotency.IdempotencyManager;
import org.hormigas.ws.core.presence.PresenceManager;
import org.hormigas.ws.core.router.context.RouterContext;
import org.hormigas.ws.core.router.stage.PipelineStage;
import org.hormigas.ws.core.router.stage.StageStatus;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.core.channel.DeliveryChannel;
import org.hormigas.ws.domain.MessageType;

import java.time.Duration;

import static org.hormigas.ws.core.router.stage.StageStatus.SKIPPED;
import static org.hormigas.ws.domain.MessageType.CHAT_ACK;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DeliveryStage implements PipelineStage<RouterContext<Message>> {

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
    public Uni<RouterContext<Message>> apply(RouterContext<Message> ctx) {
        return deliver(ctx)
                .invoke(ctx::setDelivered)
                .replaceWith(ctx)
                .onFailure().invoke(ctx::setError)
                .onFailure().recoverWithItem(ctx);
    }

    private Uni<StageStatus> deliver(RouterContext<Message> ctx) {
        var deliveryResult = isDeliverable(ctx).onItem().transformToUni(canDeliver ->
                canDeliver ? channel.deliver(ctx.getPayload()) : Uni.createFrom().item(SKIPPED));
        return messagesConfig.channelRetry().retry() ? applyRetryPolicy(deliveryResult) : deliveryResult;
    }

    private Uni<Boolean> isDeliverable(RouterContext<Message> ctx) {
        Message message = ctx.getPayload();
        if (message.getType() == CHAT_ACK) return Uni.createFrom().item(true);
        return idempotencyManager.inProgress(message)
                .flatMap(progressing -> {
                    if (progressing) return Uni.createFrom().item(Boolean.FALSE);
                    else return presenceManager.isPresent(message.getRecipientId());
                });
    }


    private Uni<StageStatus> applyRetryPolicy(Uni<StageStatus> uni) {
        return uni.onFailure().retry().withBackOff(MIN_BACKOFF, MAX_BACKOFF).atMost(MAX_RETRIES);
    }
}