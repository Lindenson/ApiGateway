package org.hormigas.ws.core.watermark.inmemory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.core.watermark.WatermarksRegistry;
import org.hormigas.ws.core.watermark.dto.Watermark;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class InmemoryWatermarkRegistry implements WatermarksRegistry {

    private static final int MAX_WATERMARKS = 10_000;

    private final Map<String, Watermark> watermarks = new ConcurrentHashMap<>();

    @Override
    public Uni<List<Watermark>> getWatermarks(int limit) {
        int effectiveLimit = Math.min(limit, MAX_WATERMARKS);
        return Uni.createFrom().item(
                watermarks.values().stream()
                        .limit(effectiveLimit)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Uni<Void> addWatermark(Watermark watermark) {
        return Uni.createFrom().item(() -> {
            watermarks.put(watermark.clientId(), watermark);

            if (watermarks.size() > MAX_WATERMARKS) {
                watermarks.entrySet().stream()
                        .sorted(Map.Entry.<String, Watermark>comparingByValue((w1, w2) ->
                                Long.compare(w1.timestamp(), w2.timestamp())
                        ))
                        .limit(watermarks.size() - MAX_WATERMARKS)
                        .map(Map.Entry::getKey)
                        .forEach(watermarks::remove);
            }

            return null;
        }).replaceWithVoid();
    }

    @Override
    public Uni<Watermark> getWatermark(String clientId) {
        return Uni.createFrom().item(watermarks.get(clientId));
    }
}
