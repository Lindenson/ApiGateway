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
public class RedisTetrisTest {

    @Inject
    RedisTetris tetris;

    @Inject
    RedisAPI redis;

    private UUID recipient1;
    private UUID recipient2;

    @BeforeEach
    public void setup() {
        recipient1 = UUID.randomUUID();
        recipient2 = UUID.randomUUID();

        // Очистка всех ключей перед тестами
        redis.flushall(List.of()).await().indefinitely();
    }

    @Test
    public void testOnSentAndComputeSafeDeleteId() {
        // Отправляем сообщения recipient1
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();

        // Отправляем сообщения recipient2
        tetris.onSent(recipient2, 10).await().indefinitely();

        // До ack safe delete id = 0 (nextExpectedId - 1 не продвинут)
        Long globalSafe = tetris.computeGlobalSafeDeleteId()
                .await().indefinitely();
        assertEquals(0L, globalSafe);
    }

    @Test
    public void testAckAdvancesNextExpectedId() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        // Ack 1
        tetris.onAck(recipient1, 1).await().indefinitely();

        // Next expected id должен продвинуться до 2, global safe delete = 1
        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(1L, globalSafe);

        // Ack 2
        tetris.onAck(recipient1, 2).await().indefinitely();
        globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(2L, globalSafe);
    }

    @Test
    public void testAckOutOfOrder() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        // Ack 3 out of order
        tetris.onAck(recipient1, 3).await().indefinitely();

        // NextExpectedId всё ещё 1, global safe = 0
        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(0L, globalSafe);

        // Ack 1
        tetris.onAck(recipient1, 1).await().indefinitely();
        globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(1L, globalSafe);

        // Ack 2
        tetris.onAck(recipient1, 2).await().indefinitely();
        globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        assertEquals(3L, globalSafe);
    }

    @Test
    public void testDisconnectAdvancesSafeDelete() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient1, 3).await().indefinitely();

        // disconnect
        tetris.onDisconnect(recipient1).await().indefinitely();

        Long globalSafe = tetris.computeGlobalSafeDeleteId().await().indefinitely();
        // disconnectCutoff = highestSentId = 3, nextExpectedId продвинут = 4, global safe = 3
        assertEquals(3L, globalSafe);
    }

    @Test
    public void testMultipleRecipients() {
        tetris.onSent(recipient1, 1).await().indefinitely();
        tetris.onSent(recipient1, 2).await().indefinitely();
        tetris.onSent(recipient2, 10).await().indefinitely();

        tetris.onAck(recipient1, 1).await().indefinitely();
        tetris.onAck(recipient2, 10).await().indefinitely();

        Long globalSafe = tetris.computeGlobalSafeDeleteId()
                .await().indefinitely();

        // recipient1 nextExpected = 2 -> safe = 1
        // recipient2 nextExpected = 11 -> safe = 10
        // global safe = min(1,10) = 1
        assertEquals(1L, globalSafe);
    }
}
