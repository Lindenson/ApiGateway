package org.hormigas.ws.infrastructure.persistance.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.SqlResult;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.pgclient.data.Interval;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.HistoryRow;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.Inserted;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxMessage;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxRow;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outbox repository using PgPool (Mutiny).
 * - insertBatch
 * - fetchBatchForProcessing (select-for-update + update lease in tx)
 * - deleteProcessedByIds
 * - insertHistoryAndOutboxTransactional
 */
@Slf4j
@ApplicationScoped
public class OutboxRepository implements PostgresOutbox {

    @Inject
    PgPool client;

    // ----------------------------
    // insertBatch
    // ----------------------------
    @Override
    public Uni<List<Inserted>> insertBatch(List<OutboxMessage> batch) {
        if (batch == null || batch.isEmpty()) return Uni.createFrom().item(Collections.emptyList());

        StringBuilder sql = new StringBuilder(
        """
        INSERT INTO outbox
         (type, sender_id, recipient_id,
         message_id, correlation_id, sender_ts, sender_tz, server_ts, payload_json, meta_json) VALUES
        """);
        List<Object> flat = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sql.append(",");
            int p = flat.size() + 1;
            sql.append("(")
                    .append("$").append(p).append(",")
                    .append("$").append(p + 1).append(",")
                    .append("$").append(p + 2).append(",")
                    .append("$").append(p + 3).append(",")
                    .append("$").append(p + 4).append(",")
                    .append("$").append(p + 5).append(",")
                    .append("$").append(p + 6).append(",")
                    .append("$").append(p + 7).append(",")
                    .append("$").append(p + 8).append(",")
                    .append("$").append(p + 9)
                    .append(")");
            OutboxMessage m = batch.get(i);
            flat.add(m.type());
            flat.add(m.senderId());
            flat.add(m.recipientId());
            flat.add(m.messageId());
            flat.add(m.correlationId());
            flat.add(m.senderTimestamp());
            flat.add(m.senderTimezone());
            flat.add(m.serverTimestamp());
            flat.add(m.payloadJson());
            flat.add(m.metaJson());
        }
        sql.append(" RETURNING id, recipient_id");

        Tuple params = Tuple.tuple();
        flat.forEach(params::addValue);

        return client.preparedQuery(sql.toString())
                .execute(params)
                .onItem().transform(rows -> {
                    List<Inserted> out = new ArrayList<>();
                        for (Row r : rows) {
                        out.add(new Inserted(r.getLong("id"), r.getString("recipient_id")));
                    }
                    return out;
                });
    }

    // ----------------------------
    // fetchBatchForProcessing
    // Steps:
    //  1) SELECT id ... FOR UPDATE SKIP LOCKED LIMIT batchSize
    //  2) UPDATE ... SET lease_until = now()+interval WHERE id = ANY($1) RETURNING ...
    // Done in one transaction (client.withTransaction)
    // ----------------------------
    @Override
    public Uni<List<OutboxRow>> fetchBatchForProcessing(int batchSize, Duration leaseDuration) {
        if (batchSize <= 0) batchSize = 50;
        long sec = Math.max(1, leaseDuration.getSeconds());
        Duration seconds = Duration.ofSeconds(sec);
        String selectSql = """
                           SELECT id FROM outbox WHERE
                            (lease_until IS NULL OR lease_until <= now()) 
                             AND status = 'PENDING' ORDER BY id LIMIT $1 FOR UPDATE SKIP LOCKED
                           """;
        String updateSql = """
                           UPDATE outbox SET lease_until = now() + ($1::interval),
                             processing_attempts = processing_attempts + 1,
                             status = 'PROCESSING' WHERE id = ANY($2) RETURNING id, type,
                             sender_id, recipient_id, message_id, correlation_id,
                             sender_ts, sender_tz, server_ts, payload_json, meta_json, created_at, lease_until
                           """;
        Interval interval = durationToPgInterval(seconds);

        int finalBatchSize = batchSize;
        return client.withTransaction(conn ->
                conn.preparedQuery(selectSql).execute(Tuple.of(finalBatchSize))
                        .flatMap(rows -> {
                            List<Long> ids = new ArrayList<>();
                            for (Row r : rows) ids.add(r.getLong("id"));
                            if (ids.isEmpty()) return Uni.createFrom().item(Collections.<OutboxRow>emptyList());
                            Tuple params = Tuple.of(interval, toLongArray(ids));
                            return conn.preparedQuery(updateSql).execute(params)
                                    .onItem().transform(updated -> {
                                        List<OutboxRow> out = new ArrayList<>();
                                        for (Row r2 : updated) {
                                            out.add(new OutboxRow(
                                                    r2.getLong("id"),
                                                    r2.getString("sender_id"),
                                                    r2.getString("recipient_id"),
                                                    r2.getString("payload_json"),
                                                    r2.getString("meta_json"),
                                                    r2.getLong("sender_ts"),
                                                    r2.getString("sender_tz"),
                                                    r2.getLong("server_ts"),
                                                    r2.getString("type"),
                                                    r2.getString("message_id"),
                                                    r2.getString("correlation_id"),
                                                    r2.getOffsetDateTime("created_at").toInstant(),
                                                    r2.getOffsetDateTime("lease_until").toInstant()
                                            ));
                                        }
                                        return out;
                                    });
                        })
        );
    }


    // ----------------------------
    // insertHistoryAndOutboxTransactional
    // Inserts message_history rows (batch) then outbox batch, in one transaction.
    // message_history.id is BIGSERIAL.
    // ----------------------------
    @Override
    public Uni<List<Inserted>> insertHistoryAndOutboxTransactional(List<HistoryRow> historyRows, List<OutboxMessage> outboxBatch) {
        return client.withTransaction(conn -> {
            Uni<Void> insertHistory = Uni.createFrom().voidItem();
            if (historyRows != null && !historyRows.isEmpty()) {
                StringBuilder hsql = new StringBuilder("INSERT INTO message_history (message_id, message_json, created_at) VALUES ");
                List<Object> flat = new ArrayList<>();
                for (int i = 0; i < historyRows.size(); i++) {
                    if (i > 0) hsql.append(",");
                    int p = flat.size() + 1;
                    hsql.append("(")
                            .append("$").append(p).append(",")
                            .append("$").append(p + 1).append("::jsonb,")
                            .append("$").append(p + 2)
                            .append(")");
                    var h = historyRows.get(i);
                    flat.add(h.messageId());
                    flat.add(h.messageJson());
                    flat.add(h.createdAt().atOffset(ZoneOffset.UTC));
                }
                hsql.append(" RETURNING id");
                Tuple params = Tuple.tuple();
                flat.forEach(params::addValue);
                insertHistory = conn.preparedQuery(hsql.toString()).execute(params).replaceWithVoid();
            }

            return insertHistory
                    .flatMap(v -> insertBatchInTx(conn, outboxBatch))
                    .onFailure().invoke(er -> log.error("Error inserting history", er));
        });
    }

    // ----------------------------
    // deleteProcessedByIds
    // ----------------------------
    @Override
    public Uni<Integer> deleteProcessedByIds(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Uni.createFrom().item(0);
        String sql = "DELETE FROM outbox WHERE message_id = ANY($1)";
        return client.preparedQuery(sql)
                .execute(Tuple.of(toStringArray(messageIds)))
                .onItem().transform(SqlResult::rowCount);
    }


    // ----------------------------
    // deleteOlderThan
    // Deletes outbox rows when id is smaller than idThreshold specified.
    // This is a Garbage Collection final stage.
    // ----------------------------
    @Override
    public Uni<Integer> deleteOlderThan(long idThreshold) {
        String sql = "DELETE FROM outbox WHERE id < $1";
        return client.preparedQuery(sql)
                .execute(Tuple.of(idThreshold))
                .onItem().transform(SqlResult::rowCount);
    }

    private Uni<List<Inserted>> insertBatchInTx(SqlConnection conn, List<OutboxMessage> batch) {
        if (batch == null || batch.isEmpty()) return Uni.createFrom().item(Collections.emptyList());

        StringBuilder sql = new StringBuilder("""
        INSERT INTO outbox (type, sender_id, recipient_id,
         message_id, correlation_id, sender_ts, sender_tz,
         server_ts, payload_json, meta_json) VALUES
        """);
        List<Object> flat = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sql.append(",");
            int p = flat.size() + 1;
            sql.append("(")
                    .append("$").append(p).append(",")
                    .append("$").append(p + 1).append(",")
                    .append("$").append(p + 2).append(",")
                    .append("$").append(p + 3).append(",")
                    .append("$").append(p + 4).append(",")
                    .append("$").append(p + 5).append(",")
                    .append("$").append(p + 6).append(",")
                    .append("$").append(p + 7).append(",")
                    .append("$").append(p + 8).append(",")
                    .append("$").append(p + 9)
                    .append(")");
            OutboxMessage m = batch.get(i);
            flat.add(m.type());
            flat.add(m.senderId());
            flat.add(m.recipientId());
            flat.add(m.messageId());
            flat.add(m.correlationId());
            flat.add(m.senderTimestamp());
            flat.add(m.senderTimezone());
            flat.add(m.serverTimestamp());
            flat.add(m.payloadJson());
            flat.add(m.metaJson());
        }
        sql.append(" RETURNING id, recipient_id");

        Tuple params = Tuple.tuple();
        flat.forEach(params::addValue);

        return conn.preparedQuery(sql.toString())
                .execute(params)
                .onItem().transform(rows -> {
                    List<Inserted> out = new ArrayList<>();
                    for (Row r : rows) {
                        out.add(new Inserted(r.getLong("id"), r.getString("recipient_id")));
                    }
                    return out;
                });
    }


    private static Interval durationToPgInterval(Duration d) {
        long totalSeconds = Math.max(1, d.getSeconds());
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        return new Interval(hours, minutes, seconds, 0);
    }

    private static Long[] toLongArray(List<Long> ids) {
        return ids.toArray(new Long[0]);
    }

    private static String[] toStringArray(List<String> ids) {
        return ids.toArray(new String[0]);
    }
}


