package org.hormigas.ws.core.watermark;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.core.watermark.dto.Watermark;

import java.util.List;

public interface WatermarksRegistry {
    Uni<List<Watermark>> getWatermarks(int limit);
    Uni<Void> addWatermark(Watermark watermark);
    Uni<Watermark> getWatermark(String clientId);
}
