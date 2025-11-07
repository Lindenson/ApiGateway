package org.hormigas.ws.websocket.utils;

import io.quarkus.websockets.next.WebSocketConnection;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.CloseReason;

import org.hormigas.ws.domen.Message;
import org.hormigas.ws.ports.channel.ws.security.JwtValidator;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketUtilsTest {

    private WebSocketUtils webSocketUtils;
    private JwtValidator jwtValidator;

    @BeforeEach
    void setUp() {
        jwtValidator = mock(JwtValidator.class);
        webSocketUtils = new WebSocketUtils();
        webSocketUtils.jwtValidator = jwtValidator;
    }

    @Test
    void testEncodeMessage_Success() {
        Message message = new Message();
        message.setId(java.util.UUID.randomUUID());
        message.setContent("Test message");

        Optional<String> encoded = webSocketUtils.encodeMessage(message);

        assertTrue(encoded.isPresent());
        assertTrue(encoded.get().contains("Test message"));
    }

    @Test
    void testEncodeMessage_NullMessage() {
        Optional<String> encoded = webSocketUtils.encodeMessage(null);

        assertTrue(encoded.isEmpty());
    }

    @Test
    void testGetValidatedClientData_ValidToken() {
        WebSocketConnection connection = mock(WebSocketConnection.class);
        HandshakeRequest handshakeRequest = mock(HandshakeRequest.class);
        when(connection.handshakeRequest()).thenReturn(handshakeRequest);
        when(handshakeRequest.header("Authorization")).thenReturn("Bearer valid.token");

        ClientData clientData = ClientData.builder()
                .id("client-123")
                .name("Test Client")
                .build();
        when(jwtValidator.validate("valid.token")).thenReturn(Optional.of(clientData));

        Optional<ClientData> result = webSocketUtils.getValidatedClientData(connection);

        assertTrue(result.isPresent());
        assertEquals("client-123", result.get().getId());
    }

    @Test
    void testGetValidatedClientData_InvalidToken() {
        WebSocketConnection connection = mock(WebSocketConnection.class);
        HandshakeRequest handshakeRequest = mock(HandshakeRequest.class);
        when(connection.handshakeRequest()).thenReturn(handshakeRequest);
        when(handshakeRequest.header("Authorization")).thenReturn("Bearer invalid.token");

        when(jwtValidator.validate("invalid.token")).thenReturn(Optional.empty());

        Optional<ClientData> result = webSocketUtils.getValidatedClientData(connection);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetValidatedClientData_MissingAuthorizationHeader() {
        WebSocketConnection connection = mock(WebSocketConnection.class);
        HandshakeRequest handshakeRequest = mock(HandshakeRequest.class);
        when(connection.handshakeRequest()).thenReturn(handshakeRequest);
        when(handshakeRequest.header("Authorization")).thenReturn(null);

        Optional<ClientData> result = webSocketUtils.getValidatedClientData(connection);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCloseReason() {
        CloseReason reason = webSocketUtils.getCloseReason();

        assertNotNull(reason);
        assertEquals(1000, reason.getCode());
        assertEquals("Invalid token", reason.getMessage());
    }
}
