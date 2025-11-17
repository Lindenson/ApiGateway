

package org.hormigas.ws.infrastructure.cache.redis;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class RedisTetrisMarkerTest {

    @Inject
    RedisTetrisMarker tetris;

    @Inject
    RedisAPI redis;

    private UUID recipient1;
    private UUID recipient2;

    @BeforeEach
    public void setup() {
        recipient1 = UUID.randomUUID();
        recipient2 = UUID.randomUUID();
        redis.flushall(List.of()).await().indefinitely();
    }

    // -----------------------------------------------------------------------------
    //   BASIC SEND / SAFE COMPUTATION
    // -----------------------------------------------------------------------------

    @Test
    public void testOnSentAndComputeSafeDeleteId() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();

        tetris.onSent(recipient2, 10).await().indefinitely();

        Long globalSafe = tetris.computeGlobalSafeDeleteId()
                .await().indefinitely();
        assertEquals(0L, globalSafe);
    }

    // -----------------------------------------------------------------------------
    //   ACK SEQUENCES
    // -----------------------------------------------------------------------------

    @Test
    public void testAckAdvancesNextExpectedId() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        tetris.onAck(recipient1, 1).await().indefinitely();
        assertEquals(1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(recipient1, 2).await().indefinitely();
        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testAckOutOfOrder() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        tetris.onAck(recipient1, 3).await().indefinitely();
        assertEquals(0L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(recipient1, 1).await().indefinitely();
        assertEquals(1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(recipient1, 2).await().indefinitely();
        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testDuplicateAckIsSafe() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();

        tetris.onAck(recipient1, 1).await().indefinitely();
        tetris.onAck(recipient1, 1).await().indefinitely();

        assertEquals(1L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testGapAckDoesNotAdvanceSafeDelete() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        tetris.onAck(recipient1, 2).await().indefinitely();

        assertEquals(0L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testLateAcksCatchUp() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        tetris.onAck(recipient1, 3).await().indefinitely();
        assertEquals(0L, tetris.computeGlobalSafeDeleteId().await().indefinitely());

        tetris.onAck(recipient1, 1).await().indefinitely();
        tetris.onAck(recipient1, 2).await().indefinitely();

        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    // -----------------------------------------------------------------------------
    //   NEW — ACK BEFORE ANY SENT
    // -----------------------------------------------------------------------------

    @Test
    public void testAckBeforeAnySentIsIgnored() {
        tetris.onAck(recipient1, 5).await().indefinitely();
        assertEquals(0L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    // -----------------------------------------------------------------------------
    //   DISCONNECT SCENARIOS
    // -----------------------------------------------------------------------------

    @Test
    public void testDisconnectAdvancesSafeDelete() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        tetris.onDisconnect(recipient1).await().indefinitely();

        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testDisconnectBeforeAnySentDoesNothing() {
        tetris.onDisconnect(recipient1).await().indefinitely();
        assertEquals(0L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    @Test
    public void testDisconnectHandlesGaps() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();
        tetris.onSent(recipient1, 4).await().indefinitely();

        tetris.onAck(recipient1, 1).await().indefinitely();

        tetris.onDisconnect(recipient1).await().indefinitely();

        assertEquals(4L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    // NEW — TEST: ACK EXACTLY AT CUTOFF BOUNDARY
    @Test
    public void testAckExactlyAtDisconnectCutoff() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();

        tetris.onDisconnect(recipient1).await().indefinitely(); // nextExpected = 3

        tetris.onSent(recipient1, 3).await().indefinitely();
        tetris.onAck(recipient1, 3).await().indefinitely();

        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    // NEW — DISCONNECT CLEANS ZSET
    @Test
    public void testDisconnectCleansAckZset() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();

        tetris.onAck(recipient1, 1).await().indefinitely();
        tetris.onAck(recipient1, 2).await().indefinitely();

        tetris.onDisconnect(recipient1).await().indefinitely();

        String ackKey = "tetris:recipient:" + recipient1 + ":acks";

        String count = redis.zcount(ackKey, "-inf", "+inf")
                .await().indefinitely()
                .toString();

        assertEquals("0", count);
        assertEquals(2L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    // NEW — ACK AFTER DISCONNECT
    @Test
    public void testAckAfterDisconnectWorksNormally() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();

        tetris.onDisconnect(recipient1).await().indefinitely(); // nextExpected=3

        tetris.onSent(recipient1, 3).await().indefinitely();
        tetris.onAck(recipient1, 3).await().indefinitely();

        assertEquals(3L, tetris.computeGlobalSafeDeleteId().await().indefinitely());
    }

    // -----------------------------------------------------------------------------
    //   MULTIPLE RECIPIENTS
    // -----------------------------------------------------------------------------

    @Test
    public void testMultipleRecipients() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient2, 10).await().indefinitely();

        tetris.onAck(recipient1, 1).await().indefinitely();
        tetris.onAck(recipient2, 10).await().indefinitely();

        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(1L, globalSafe);
    }

    @Test
    public void testOneRecipientBlocksGlobal() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient2, 10).await().indefinitely();
        tetris.onSent(recipient2, 11).await().indefinitely();

        tetris.onAck(recipient2, 10).await().indefinitely();
        tetris.onAck(recipient2, 11).await().indefinitely();

        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(0L, globalSafe);
    }

    // -----------------------------------------------------------------------------
    //   NEW — SCAN MULTI-ITERATION TEST
    // -----------------------------------------------------------------------------

    @Test
    public void testScanMultiIteration() {
        for (int i = 0; i < 150; i++) {
            UUID r = UUID.randomUUID();
            tetris.onSent(r, 5).await().indefinitely();
            tetris.onAck(r, 5).await().indefinitely();
        }

        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();

        assertEquals(5L, globalSafe);
    }
}
