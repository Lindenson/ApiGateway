package org.hormigas.ws.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String messageId;
    private String correlationId;
    private String conversationId;

    private MessageType type;

    private long clientTimestamp;
    private String clientTimezone;
    private long serverTimestamp;
    private long previousServerTimestamp;
    private long ttl;

    private boolean requiresAck;
    private boolean durable;
    private boolean persistent;

    private int creditsAvailable;

    private String senderId;
    private String recipientId;

    private Payload payload;
    private Map<String, String> meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private String kind;
        private String body;
    }
}
