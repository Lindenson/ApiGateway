package org.hormigas.ws.core.router.pipeline;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.router.PipelineResolver;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.domain.MessageType;

import java.util.EnumMap;
import java.util.Map;

import static org.hormigas.ws.core.router.PipelineResolver.PipelineType.*;
import static org.hormigas.ws.domain.MessageType.*;


@Slf4j
@ApplicationScoped
public class MessagePipelineResolver implements PipelineResolver<Message, MessageType> {

    private final Map<MessageType, PipelineType> routingMatrix = new EnumMap<>(MessageType.class);

    public MessagePipelineResolver() {

        // ASK routing
        routingMatrix.put(SIGNAL_ASK, CACHED_ACK);
        routingMatrix.put(CHAT_ASK, PERSISTENT_ACK);

        // CHAT routing
        routingMatrix.put(CHAT_OUT, CACHED_OUT);
        routingMatrix.put(CHAT_IN, PERSISTENT_OUT);

        // SIGNAL routing
        routingMatrix.put(SIGNAL_OUT, CACHED_OUT);

        // SERVICE routing
        routingMatrix.put(SERVICE_OUT, DIRECT_OUT);
    }

    @Override
    public PipelineType resolvePipeline(Message message) {
        if (message == null || message.getType() == null) {
            throw new IllegalArgumentException("Message or its type/origin cannot be null");
        }

        PipelineType policy = routingMatrix.get(message.getType());

        if (policy == null) {
            log.warn("No routing policy defined for message type={}", message.getType());
            return DIRECT_OUT;
        }

        log.debug("Resolved policy {} for message type={}", policy, message.getType());
        return policy;
    }
}
