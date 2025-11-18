package org.hormigas.ws.infrastructure.persistance.postgres;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.hormigas.ws.domain.generator.IdGenerator;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.Inserted;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OutboxRepositoryTest {

    @Inject
    PgPool client;

    @Inject
    OutboxRepository repo;

    @Inject
    IdGenerator idGenerator;

    @BeforeAll
    public void setupSchema() {
        client.query("""
                CREATE TABLE IF NOT EXISTS message_history (
                    id BIGSERIAL PRIMARY KEY,
                    message_id VARCHAR(128) NOT NULL,
                    message_json JSONB NOT NULL,
                    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_message_history_message_id ON message_history(message_id);
                CREATE TABLE IF NOT EXISTS outbox (
                    id BIGSERIAL PRIMARY KEY,
                    type VARCHAR(64) NOT NULL,
                    sender_id VARCHAR(128) NOT NULL,
                    recipient_id VARCHAR(128) NOT NULL,
                    message_id VARCHAR(128) NOT NULL,
                    correlation_id VARCHAR(128),
                    sender_ts BIGINT NOT NULL,
                    sender_tz VARCHAR(64),
                    server_ts BIGINT NOT NULL,
                    payload_json JSONB NOT NULL,
                    meta_json JSONB,
                    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
                    lease_until TIMESTAMPTZ,
                    processing_attempts INT DEFAULT 0 NOT NULL,
                    status VARCHAR(32) DEFAULT 'PENDING' NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_outbox_recipient_id ON outbox(recipient_id);
                CREATE INDEX IF NOT EXISTS idx_outbox_status_lease ON outbox(status, lease_until);
                CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox(created_at);
                """).execute().await().indefinitely();
    }

    @BeforeEach
    public void cleanup() {
        client.query("TRUNCATE outbox, message_history RESTART IDENTITY").execute().await().indefinitely();
    }

    private OutboxMessage sample(String sender, String recipient, int idx) {
        String payload = "{\"kind\":\"text\",\"body\":\"hello " + idx + "\"}";
        String meta = "{\"k\":\"v\"}";
        return new OutboxMessage(
                sender, recipient,
                "CHAT",
                "msg-" + idx,
                null,
                Instant.now().toEpochMilli(),
                "UTC",
                Instant.now().toEpochMilli(),
                payload,
                meta
        );
    }

    private HistoryRow sampleHistory(int idx) {
        String msgId = "msg-" + idx;
        String json = "{\"messageId\":\"" + msgId + "\",\"payload\":{\"kind\":\"text\",\"body\":\"hello " + idx + "\"}}";
        return new HistoryRow(msgId, json, Instant.now());
    }

    @Test
    public void testInsertHistoryAndOutboxTransactional() {
        var h = sampleHistory(1);
        var o = sample(idGenerator.generateId(), idGenerator.generateId(), 1);

        List<Inserted> res = repo.insertHistoryAndOutboxTransactional(List.of(h), List.of(o))
                .await().indefinitely();
        assertEquals(1, res.size());

        var rows = client.query("SELECT count(*) FROM outbox").execute().await().indefinitely();
        assertEquals(1L, rows.iterator().next().getLong(0));
    }

    @Test
    public void testFetchBatchForProcessingLeasesAndReturnsRows() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();
        repo.insertBatch(List.of(sample(s, r, 1), sample(s, r, 2), sample(s, r, 3)))
                .await().indefinitely();

        var batch = repo.fetchBatchForProcessing(2, Duration.ofSeconds(5)).await().indefinitely();
        assertEquals(2, batch.size());
        assertTrue(batch.get(0).leaseUntil().isAfter(Instant.now()));

        var batch2 = repo.fetchBatchForProcessing(10, Duration.ofSeconds(5)).await().indefinitely();
        assertEquals(1, batch2.size());

        List<String> idsToDelete = List.of(batch.get(0).messageId(), batch.get(1).messageId());
        int deleted = repo.deleteProcessedByIds(idsToDelete).await().indefinitely();
        assertEquals(2, deleted);

        var batch3 = repo.fetchBatchForProcessing(5, Duration.ofSeconds(1)).await().indefinitely();
        assertEquals(0, batch3.size());
    }

    @Test
    public void testInsertBatchReturnIds() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();
        var res = repo.insertBatch(List.of(sample(s, r, 10), sample(s, r, 11))).await().indefinitely();
        assertEquals(2, res.size());
        assertTrue(res.get(0).id() > 0);
    }

    @Test
    public void testLeasePreventsReprocessing() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();
        repo.insertBatch(List.of(sample(s, r, 1))).await().indefinitely();

        var batch1 = repo.fetchBatchForProcessing(10, Duration.ofSeconds(5)).await().indefinitely();
        assertEquals(1, batch1.size());

        var batch2 = repo.fetchBatchForProcessing(10, Duration.ofSeconds(5)).await().indefinitely();
        assertEquals(0, batch2.size());
    }


    @Test
    public void testProcessingAttemptsIncrement() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();
        repo.insertBatch(List.of(sample(s, r, 1))).await().indefinitely();

        repo.fetchBatchForProcessing(10, Duration.ofSeconds(5)).await().indefinitely();

        var row = client.query("SELECT processing_attempts FROM outbox LIMIT 1").execute().await().indefinitely().iterator().next();
        assertEquals(1, row.getInteger("processing_attempts"));
    }


    @Test
    public void testStatusUpdatedToProcessing() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();
        repo.insertBatch(List.of(sample(s, r, 1))).await().indefinitely();

        repo.fetchBatchForProcessing(10, Duration.ofSeconds(5)).await().indefinitely();

        var row = client.query("SELECT status FROM outbox LIMIT 1").execute().await().indefinitely().iterator().next();
        assertEquals("PROCESSING", row.getString("status"));
    }


    @Test
    public void testTransactionalRollback() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();
        var h = new HistoryRow(s, "{json}", Instant.now());
        var o = sample(s + s + s + s + s + s, r, 1); // six times more id than permitted

        try {
            repo.insertHistoryAndOutboxTransactional(List.of(h), List.of(o))
                    .await().indefinitely();
            Assertions.fail("Should fail");
        } catch (Exception ignored) {
        }

        var count1 = client.query("SELECT count(*) FROM message_history")
                .execute().await().indefinitely()
                .iterator().next().getLong(0);
        var count2 = client.query("SELECT count(*) FROM outbox")
                .execute().await().indefinitely()
                .iterator().next().getLong(0);

        assertEquals(0L, count1);
        assertEquals(0L, count2);

    }

    @Test
    public void testDeleteOlderThan() {
        String s = idGenerator.generateId();
        String r = idGenerator.generateId();

        List<OutboxMessage> batch = List.of(
                sample(s, r, 1),
                sample(s, r, 2),
                sample(s, r, 3)
        );
        repo.insertBatch(batch).await().indefinitely();

        long countBefore = client.query("SELECT count(*) FROM outbox")
                .execute().await().indefinitely()
                .iterator().next().getLong(0);
        assertEquals(3L, countBefore);

        int deleted = repo.deleteOlderThan(3L).await().indefinitely();
        assertEquals(2, deleted);

        long countAfter = client.query("SELECT count(*) FROM outbox")
                .execute().await().indefinitely()
                .iterator().next().getLong(0);
        assertEquals(1L, countAfter);

        long remainingId = client.query("SELECT id FROM outbox")
                .execute().await().indefinitely()
                .iterator().next().getLong("id");
        assertEquals(3L, remainingId);
    }
}
