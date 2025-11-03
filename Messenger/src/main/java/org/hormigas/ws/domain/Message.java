package org.hormigas.ws.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.Map;

@Builder
@Getter
@EqualsAndHashCode
@NoArgsConstructor(force = true)
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Message.MessageBuilder.class)
public class Message {

    private final String messageId;
    private final String correlationId;
    private final String conversationId;

    private final MessageType type;

    private final long clientTimestamp;
    private final String clientTimezone;
    private final long serverTimestamp;
    private final long previousServerTimestamp;
    private final long ttl;

    private final boolean requiresAck;
    private final boolean durable;
    private final boolean persistent;

    private final int creditsAvailable;

    private final String senderId;
    private final String recipientId;

    private final Payload payload;
    private final Map<String, String> meta;

    @Getter
    @Builder
    @EqualsAndHashCode
    @NoArgsConstructor(force = true)
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = Payload.PayloadBuilder.class)
    public static class Payload {
        private final String kind;
        private final String body;
    }
}