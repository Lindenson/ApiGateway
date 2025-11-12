package org.hormigas.ws.websocket;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.Json;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.ports.channel.ws.mappers.dto.SocketMessage;
import org.hormigas.ws.infrastructure.websocket.security.JwtValidator;
import org.hormigas.ws.domain.session.ClientData;
import org.hormigas.ws.service.ClientMessagePersistence;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hormigas.ws.mother.MessageCreator.getMessageMessageClientId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class MessengerWebSocketTest {

    @InjectMock
    JwtValidator jwtValidator;

    @InjectMock
    ClientMessagePersistence messagePersistence;

    @TestHTTPResource("/ws")
    URI uri;


    @Test
    public void testWebsocketChat() throws Exception {
        Message message = getMessageMessageClientId(UUID.randomUUID());
        SocketMessage socketMessage = SocketMessage.builder().content(message.getContent()).id(message.getId().toString()).build();

        when(jwtValidator.validate(anyString())).thenReturn(Optional.of(ClientData.builder().id(message.getClientId()).build()));
        when(messagePersistence.getNextBatchToSend()).thenReturn(Uni.createFrom().item(List.of(message)));

        CountDownLatch messageLatch = new CountDownLatch(1);
        List<SocketMessage> messages = new CopyOnWriteArrayList<>();
        Vertx vertx = Vertx.vertx();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            client.connect(new WebSocketConnectOptions()
                            .setHost(uri.getHost())
                            .setPort(uri.getPort())
                            .setURI(uri.getPath())
                            .setHeaders(HeadersMultiMap.headers().add("Authorization", "Bearer token")))
                    .onSuccess(
                            ws -> {
                                ws.textMessageHandler(m -> {
                                    messages.add(Json.decodeValue(m, SocketMessage.class));
                                    messageLatch.countDown();
                                });
                                ws.writeTextMessage("{\"ackId\": \"" + socketMessage.getId() + "\"}");
                            });
            assertTrue(messageLatch.await(5, TimeUnit.SECONDS), messageLatch.toString());
            assertEquals(socketMessage, messages.getFirst());

        } finally {
            client.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            vertx.close();
        }

        verify(messagePersistence, times(1)).removeAcknowledgedMessages(eq(List.of(message.getId().toString())));
    }
}