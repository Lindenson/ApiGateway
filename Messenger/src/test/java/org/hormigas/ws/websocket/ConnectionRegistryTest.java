//package org.hormigas.ws.websocket;
//
//import io.quarkus.websockets.next.WebSocketConnection;
//import org.hormigas.ws.ports.channel.presense.inmemory.LocalRegistry;
//import org.hormigas.ws.ports.channel.ws.security.dto.ClientData;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//
//import static io.smallrye.common.constraint.Assert.assertTrue;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//class ConnectionRegistryTest {
//
//    private LocalRegistry<WebSocketConnection> registry;
//
//    @BeforeEach
//    void setUp() {
//        registry = new LocalRegistry<WebSocketConnection>(null);
//    }
//
//    @Test
//    void testRegisterClient() {
//        WebSocketConnection connection = Mockito.mock(WebSocketConnection.class);
//        Mockito.when(connection.id()).thenReturn("conn-1");
//
//        ClientData clientData = ClientData.builder()
//                .clientId("client-123")
//                .clientName("Test Client")
//                .build();
//
//        registry.registerClient(clientData, connection);
//
//        assertEquals(1, registry.().size());
//        assertTrue(registry.getConnections().stream()
//                .anyMatch(c -> c.getId().equals("client-123") && c.getWsConnection().id().equals("conn-1")));
//    }
//
//    @Test
//    void testDeregisterConnection() {
//        WebSocketConnection connection = Mockito.mock(WebSocketConnection.class);
//        Mockito.when(connection.id()).thenReturn("conn-2");
//
//        ClientData clientData = ClientData.builder()
//                .clientId("client-456")
//                .clientName("Another Client")
//                .build();
//
//        registry.registerClient(clientData, connection);
//        assertEquals(1, registry.getConnections().size());
//
//        registry.deregisterConnection(connection);
//
//        assertEquals(0, registry.getConnections().size());
//    }
//}
//
