package org.hormigas.ws.infrastructure.cache.inmemory.idempotency;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Потокобезопасная структура с порядком вставки и ограничением размера.
 * O(1) на put, get, remove.
 */
@Slf4j
public class ConcurrentInsertionOrderMap<K, V> {

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<K> order = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();
    private final int maxSize;
    private final Predicate<V> vPredicate;

    private final int MULTIPLICATION_FACTOR = 3;

    public ConcurrentInsertionOrderMap(int maxSize, Predicate<V> vPredicate) {
        this.maxSize = maxSize;
        this.vPredicate = vPredicate;
    }

    public ConcurrentInsertionOrderMap() {
        this(Integer.MAX_VALUE, e -> false);
    }

    public V put(K key, V value) {
        if (key == null || value == null) return null;

        V prev = map.putIfAbsent(key, value);
        if (prev == null) {
            order.addLast(key);
            int current = size.incrementAndGet();
            if (current > maxSize) {
                cleanupIfNeeded();
            }
        }
        return prev;
    }

    public V get(K key) {
        if (key == null) return null;
        return map.get(key);
    }

    public V replace(K key, V value) {
        if (key == null || value == null) return null;
        return map.replace(key, value);
    }

    public V remove(K key) {
        if (key == null) return null;

        V removed = map.remove(key);
        if (removed != null) {
            order.remove(key);
            size.decrementAndGet();
        }
        return removed;
    }

    public V peekFirst() {
        for (K key : order) {
            V val = map.get(key);
            if (val != null) return val;
        }
        return null;
    }

    public V pollFirst() {
        while (true) {
            K key = order.pollFirst();
            if (key == null) return null;
            V val = map.remove(key);
            if (val != null) {
                size.decrementAndGet();
                return val;
            }
        }
    }

    public int size() {
        return size.get();
    }

    private void cleanupIfNeeded() {
        int steps = size.get() - maxSize;
        while (steps-- > 0) {
            K oldest = order.pollFirst();
            if (oldest == null) return;
            V candidate = map.get(oldest);
            if (candidate == null) continue;
            if (!vPredicate.test(candidate)) continue;
            if (map.remove(oldest, candidate)) {
                size.decrementAndGet();
            }
        }

        if (size.get() > maxSize * MULTIPLICATION_FACTOR) {
            log.warn("⚠️ Cleanup incomplete, force truncating to {}", maxSize);
            truncate(maxSize);
        }

        log.debug("🧹 Cleaned to size {}", size.get());
    }

    public void truncate(int targetSize) {
        if (targetSize < 0) targetSize = 0;
        while (size.get() > targetSize) {
            K oldest = order.pollFirst();
            if (oldest == null) break;
            if (map.remove(oldest) != null) {
                size.decrementAndGet();
            }
        }
    }
}
