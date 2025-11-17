package org.hormigas.ws.infrastructure.cache.inmemory.watermark;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.ports.watermark.WatermarksRegistry;
import org.hormigas.ws.domain.watremark.Watermark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@ApplicationScoped
public class InMemoryWatermarkRegistry implements WatermarksRegistry {

    private static final int MAX_WATERMARKS = 10_000;

    private final ConcurrentHashMap<String, Watermark> map = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> order = new ConcurrentLinkedDeque<>();

    @Override
    public Uni<Void> add(Watermark watermark) {
        return Uni.createFrom().item(() -> {
            String clientId = watermark.clientId();
            Watermark old = map.put(clientId, watermark);
            if (old == null) {
                order.addLast(clientId);
            }
            while (map.size() > MAX_WATERMARKS) {
                String oldest = order.pollFirst();
                if (oldest == null) break;
                map.remove(oldest);
            }
            return null;
        }).replaceWithVoid();
    }


    @Override
    public Uni<List<Watermark>> fetchBatch(int limit) {
        int effectiveLimit = Math.min(limit, MAX_WATERMARKS);
        return Uni.createFrom().item(() -> {
            List<Watermark> result = new ArrayList<>(effectiveLimit);
            for (int i = 0; i < effectiveLimit; i++) {
                String clientId = order.pollFirst();
                if (clientId == null) break;

                Watermark watermark = map.remove(clientId);
                if (watermark != null) {
                    result.add(watermark);
                }
            }
            return result;
        });
    }
}
