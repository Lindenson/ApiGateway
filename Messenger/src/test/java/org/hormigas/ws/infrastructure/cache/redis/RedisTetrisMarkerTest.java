
package org.hormigas.ws.infrastructure.cache.redis;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.inject.Inject;
import org.hormigas.ws.domain.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class RedisTetrisMarkerTest {

    @Inject
    RedisTetrisMarker tetris;

    @Inject
    RedisAPI redis;

    private String recipient1;
    private String recipient2;

    @BeforeEach
    public void setup() {
        recipient1 = UUID.randomUUID().toString();
        recipient2 = UUID.randomUUID().toString();
        redis.flushall(List.of()).await().indefinitely();
    }

    @Test
    public void testOnSentAndComputeSafeDeleteId() {
        Message message1 = msg(1L, recipient1);
        Message message2 = msg(2L, recipient1);
        tetris.onSent(message1).await().indefinitely();
        tetris.onSent(message2).await().indefinitely();

        Message message10 = msg(10L, recipient2);;
        tetris.onSent(message10).await().indefinitely();

        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(1L, globalSafe);

        var gz = redis.zrange(List.of("tetris:minids", "0", "-1")).await().indefinitely();
        assertNotNull(gz);
    }

    @Test
    public void testAckAdvancesNextExpectedId() {
        Message message3 = msg(3L, recipient1);
        Message message4 = msg(4L, recipient1);
        Message message6 = msg(6L, recipient1);

        tetris.onSent(message3).await().indefinitely();
        tetris.onSent(message4).await().indefinitely();
        tetris.onSent(message6).await().indefinitely();

        tetris.onAck(message6).await().indefinitely();
        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(message3).await().indefinitely();
        assertEquals(4L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testAckOutOfOrder() {
        Message message1 = msg(1L, recipient1);
        Message message2 = msg(2L, recipient1);
        Message message3 = msg(3L, recipient1);

        tetris.onSent(message1).await().indefinitely();
        tetris.onSent(message2).await().indefinitely();
        tetris.onSent(message3).await().indefinitely();

        tetris.onAck(message3).await().indefinitely();
        assertEquals(1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(message1).await().indefinitely();
        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(message2).await().indefinitely();
        assertEquals(-1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testDuplicateAckIsSafe() {
        Message message1 = msg(1L, recipient1);
        Message message2 = msg(2L, recipient1);
        tetris.onSent(message1).await().indefinitely();
        tetris.onSent(message2).await().indefinitely();

        tetris.onAck(message1).await().indefinitely();
        tetris.onAck(message1).await().indefinitely();

        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

//    @Test
//    public void testGapAckDoesNotAdvanceSafeDelete() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient1, 3).await().indefinitely();
//
//        tetris.onAck(recipient1, 2).await().indefinitely();
//
//        assertEquals(1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testLateAcksCatchUp() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient1, 3).await().indefinitely();
//
//        tetris.onAck(recipient1, 3).await().indefinitely();
//        assertEquals(1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//
//        tetris.onAck(recipient1, 1).await().indefinitely();
//        tetris.onAck(recipient1, 2).await().indefinitely();
//
//        assertEquals(-1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testAckBeforeAnySentIsIgnored() {
//        tetris.onAck(recipient1, 5).await().indefinitely();
//        assertEquals(-1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testDisconnectAdvancesSafeDelete() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient1, 3).await().indefinitely();
//
//        tetris.onDisconnect(recipient1).await().indefinitely();
//
//        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testDisconnectBeforeAnySentDoesNothing() {
//        tetris.onDisconnect(recipient1).await().indefinitely();
//        assertEquals(-1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testDisconnectHandlesGaps() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient1, 3).await().indefinitely();
//        tetris.onSent(recipient1, 4).await().indefinitely();
//
//        tetris.onAck(recipient1, 1).await().indefinitely();
//
//        tetris.onDisconnect(recipient1).await().indefinitely();
//

    /// / highestSentId=4 -> safe=4
//        assertEquals(4L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testAckExactlyAtDisconnectCutoff() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//
//        tetris.onDisconnect(recipient1).await().indefinitely(); // safe=2
//
//        tetris.onSent(recipient1, 3).await().indefinitely();
//        tetris.onAck(recipient1, 3).await().indefinitely();
//
//        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testDisconnectCleansAckZset() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//
//        tetris.onDisconnect(recipient1).await().indefinitely();
//        String ackKey = "tetris:re:" + recipient1 + ":ack";
//
//        String count = redis.zcount(ackKey, "-inf", "+inf")
//                .await().indefinitely()
//                .toString();
//
//        assertEquals("1", count);
//        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testAckAfterDisconnectWorksNormally() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//
//        tetris.onDisconnect(recipient1).await().indefinitely(); // safe = 2
//
//        tetris.onSent(recipient1, 3).await().indefinitely();
//        tetris.onAck(recipient1, 3).await().indefinitely();
//
//        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
//    }
//
//    @Test
//    public void testMultipleRecipients() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient2, 10).await().indefinitely();
//
//        tetris.onAck(recipient1, 1).await().indefinitely();
//        tetris.onAck(recipient2, 10).await().indefinitely();
//
//        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
//        assertEquals(2L, globalSafe);
//    }
//
//    @Test
//    public void testOneRecipientBlocksGlobal() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient2, 10).await().indefinitely();
//        tetris.onSent(recipient2, 11).await().indefinitely();
//
//        tetris.onAck(recipient2, 10).await().indefinitely();
//        tetris.onAck(recipient2, 11).await().indefinitely();
//
//        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
//        assertEquals(1L, globalSafe);
//    }
//
//    @Test
//    public void testScanMultiIteration() {
//        for (int i = 0; i < 150; i++) {
//            String r = UUID.randomUUID().toString();
//            tetris.onSent(r, 5).await().indefinitely();
//            tetris.onAck(r, 5).await().indefinitely();
//        }
//
//        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
//        assertEquals(-1L, globalSafe);
//    }
//
//    @Test
//    public void testMultipleClientsOneEmptyOneNot() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onAck(recipient1, 1).await().indefinitely();
//        tetris.onAck(recipient1, 2).await().indefinitely();
//
//        tetris.onSent(recipient2, 10).await().indefinitely();
//        tetris.onSent(recipient2, 12).await().indefinitely();
//
//        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
//        assertEquals(10L, globalSafe);
//
//        var z1 = redis.zrange(List.of("tetris:re:" + recipient1 + ":ack", "0", "-1")).await().indefinitely();
//        assertEquals(0, z1.size(), "recipient1 acks should be empty");
//
//        var z2 = redis.zrange(List.of("tetris:re:" + recipient2 + ":ack", "0", "-1")).await().indefinitely();
//        assertEquals(List.of("10", "12").toString(), z2.toString(), "recipient2 should have unacked messages");
//    }
//
//    @Test
//    public void testZeroIdsIgnored() {
//        tetris.onSent(recipient1, 0).await().indefinitely();
//        tetris.onSent(recipient2, 5).await().indefinitely();
//
//        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
//        assertEquals(5L, globalSafe);
//    }
//
//    @Test
//    public void testGlobalSafeBecomesMinusOneIfAllZeroOrEmpty() {
//        tetris.onSent(recipient1, 0).await().indefinitely();
//        tetris.onAck(recipient1, 0).await().indefinitely();
//
//        tetris.onSent(recipient2, 0).await().indefinitely();
//        tetris.onAck(recipient2, 0).await().indefinitely();
//
//        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
//        assertEquals(-1L, globalSafe);
//    }
//
//    @Test
//    public void testFindHeavyClientsNone() {
//        // нет клиентов
//        var heavy = tetris.findHeavyClients(2, 10).await().indefinitely();
//        assertTrue(heavy.isEmpty(), "No heavy clients expected");
//    }
//
//    @Test
//    public void testFindHeavyClientsAllBelowThreshold() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient2, 1).await().indefinitely();
//
//        var heavy = tetris.findHeavyClients(3, 10).await().indefinitely();
//        assertTrue(heavy.isEmpty(), "No client exceeds threshold 3");
//    }
//
//    @Test
//    public void testFindHeavyClientsSomeAboveThreshold() {
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient1, 3).await().indefinitely();
//
//        tetris.onSent(recipient2, 1).await().indefinitely();
//
//        var heavy = tetris.findHeavyClients(2, 10).await().indefinitely();
//        assertEquals(1, heavy.size());
//        assertTrue(heavy.get(0).contains(recipient1));
//        assertFalse(heavy.get(0).contains(recipient2));
//    }
//
//    @Test
//    public void testFindHeavyClientsRespectsLimit() {
//        for (int i = 0; i < 5; i++) {
//            String r = UUID.randomUUID().toString();
//            tetris.onSent(r, 1).await().indefinitely();
//            tetris.onSent(r, 2).await().indefinitely();
//            tetris.onSent(r, 3).await().indefinitely();
//        }
//
//        var heavy = tetris.findHeavyClients(2, 3).await().indefinitely();
//        assertEquals(3, heavy.size(), "Should respect limit");
//    }
//
//    @Test
//    public void testFindHeavyClientsIgnoresAcked() {
//        // recipient1 = 3 unacked initially, then ack 2
//        tetris.onSent(recipient1, 1).await().indefinitely();
//        tetris.onSent(recipient1, 2).await().indefinitely();
//        tetris.onSent(recipient1, 3).await().indefinitely();
//
//        tetris.onAck(recipient1, 2).await().indefinitely();
//
//        var heavy = tetris.findHeavyClients(2, 10).await().indefinitely();
//        assertEquals(1, heavy.size());
//        assertTrue(heavy.get(0).contains(recipient1));
//    }
//
//    @Test
//    public void testFindHeavyClientsWithZeroIds() {
//        tetris.onSent(recipient1, 0).await().indefinitely();
//        tetris.onSent(recipient2, 1).await().indefinitely();
//        tetris.onSent(recipient2, 2).await().indefinitely();
//        tetris.onSent(recipient2, 3).await().indefinitely();
//
//        var heavy = tetris.findHeavyClients(2, 10).await().indefinitely();
//        assertEquals(1, heavy.size());
//        assertTrue(heavy.get(0).contains(recipient2));
//        assertFalse(heavy.get(0).contains(recipient1));
//    }
    private Message msg(long id, String recipientId) {
        return Message.builder()
                .id(id)
                .ackId(id)
                .recipientId(recipientId)
                .build();
    }
}
