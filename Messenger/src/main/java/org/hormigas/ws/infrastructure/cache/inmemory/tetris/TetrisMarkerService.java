package org.hormigas.ws.infrastructure.cache.inmemory.tetris;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.stage.StageResult;
import org.hormigas.ws.ports.tetris.TetrisMarker;

import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TetrisService is responsible for tracking sent messages, acknowledgments (acks), and disconnected recipients.
 * <p>
 * It maintains per-recipient state to determine the safe message ID that can be deleted or garbage-collected globally.
 * Each recipient has a pointer (nextExpectedId) that represents the first message ID that cannot yet be deleted.
 * Messages acknowledged out-of-order are tracked in a TreeSet. Disconnected recipients advance the pointer to
 * mark all messages sent before disconnect as safe to delete.
 */
public class TetrisMarkerService implements TetrisMarker<Message> {

    /**
     * Mapping from recipient UUID to their current state.
     */
    private final ConcurrentMap<String, RecipientState> map = new ConcurrentHashMap<>();

    /**
     * Registers that a message with the given ID has been sent to the specified recipient.
     *
     * @param recipientId the recipient's UUID
     * @param id          the sent message ID
     */
    @Override
    public Uni<StageResult<Message>> onSent(Message message) {
        map.computeIfAbsent(message.getRecipientId(), k -> new RecipientState())
                .onSent(message.getAckId());
        return Uni.createFrom().item(StageResult.passed());
    }

    /**
     * Registers that a message with the given ID has been acknowledged by the specified recipient.
     *
     * @param recipientId the recipient's UUID
     * @param id          the acknowledged message ID
     */
    @Override
    public Uni<StageResult<Message>> onAck(Message message) {
        RecipientState s = map.get(message.getRecipientId());
        if (s != null) s.onAck(message.getAckId());
        return Uni.createFrom().item(StageResult.passed());
    }

    /**
     * Marks the recipient as disconnected. All messages sent to this recipient up to their
     * highest sent ID are considered safe to delete.
     *
     * @param recipientId the recipient's UUID
     */
    @Override
    public Uni<StageResult<Message>> onDisconnect(String recipientId) {
        RecipientState s = map.get(recipientId);
        if (s != null) s.onDisconnect();
        return Uni.createFrom().item(StageResult.passed());
    }

    /**
     * Computes the global safe delete ID across all recipients.
     * This is the maximum ID that can be safely deleted because all recipients
     * have either acknowledged messages up to that ID or disconnected.
     *
     * @return the highest globally safe message ID to delete
     */
    @Override
    public Uni<Long> computeGlobalSafeDeleteId() {
        long global = Long.MAX_VALUE;
        boolean haveAny = false;
        for (RecipientState s : map.values()) {
            long safe = s.getSafeDeleteId();
            haveAny = true;
            if (safe < global) global = safe;
        }
        return Uni.createFrom().item(haveAny ? global : 0L);
    }

    @Override
    public Uni<List<String>> findHeavyClients(int threshold, int limit) {
        return Uni.createFrom().item(List.of());
    }

    /**
     * Represents the per-recipient state, tracking sent messages, acknowledgments,
     * and disconnected cutoff.
     */
    static class RecipientState {

        /**
         * Lock to ensure thread-safety of updates to the recipient state.
         */
        private final ReentrantLock lock = new ReentrantLock();

        /**
         * The first message ID that cannot yet be safely deleted.
         * Initialized to -1 when no messages have been sent.
         */
        private long nextExpectedId = -1L;

        /**
         * The highest message ID that has been sent to this recipient.
         */
        private long highestSentId = 0L;

        /**
         * All message IDs less than or equal to this value can be considered "not needed"
         * because the recipient has disconnected.
         */
        private long disconnectCutoffId = 0L;

        /**
         * Tracks message IDs acknowledged out-of-order, i.e., IDs >= nextExpectedId.
         */
        private final NavigableSet<Long> ackedOutOfOrder = new TreeSet<>();

        /**
         * Updates state when a message is sent.
         * Sets the nextExpectedId if this is the first message for the recipient.
         *
         * @param id the sent message ID
         */
        public void onSent(long id) {
            lock.lock();
            try {
                highestSentId = Math.max(highestSentId, id);
                if (nextExpectedId == -1L) {
                    nextExpectedId = id;
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Updates state when a message is acknowledged.
         * Advances nextExpectedId while messages are covered either by ackedOutOfOrder or disconnectCutoffId.
         *
         * @param id the acknowledged message ID
         */
        public void onAck(long id) {
            lock.lock();
            try {
                if (nextExpectedId == -1L) {
                    nextExpectedId = id + 1;
                    return;
                }
                if (id < nextExpectedId) return;
                ackedOutOfOrder.add(id);
                advanceWhilePossible();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Marks the recipient as disconnected. All messages sent up to highestSentId
         * are now considered safe to delete.
         */
        public void onDisconnect() {
            lock.lock();
            try {
                if (highestSentId > disconnectCutoffId) {
                    disconnectCutoffId = highestSentId;
                    advanceWhilePossible();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Advances nextExpectedId while possible.
         * nextExpectedId is increased if:
         * - it is less than or equal to disconnectCutoffId
         * - the ID exists in ackedOutOfOrder
         */
        private void advanceWhilePossible() {
            while (true) {
                long cur = nextExpectedId;
                if (cur == -1L) break;
                if (cur <= disconnectCutoffId) {
                    nextExpectedId = cur + 1;
                    continue;
                }
                Long firstAck = ackedOutOfOrder.isEmpty() ? null : ackedOutOfOrder.first();
                if (firstAck != null && firstAck.equals(cur)) {
                    ackedOutOfOrder.remove(firstAck);
                    nextExpectedId = cur + 1;
                    continue;
                }
                break;
            }
        }

        /**
         * Returns the safe delete ID for this recipient.
         * Messages <= this ID can be safely removed.
         *
         * @return the safe delete ID
         */
        public long getSafeDeleteId() {
            lock.lock();
            try {
                if (nextExpectedId == -1L) {
                    return Long.MAX_VALUE;
                }
                return nextExpectedId - 1;
            } finally {
                lock.unlock();
            }
        }
    }
}
