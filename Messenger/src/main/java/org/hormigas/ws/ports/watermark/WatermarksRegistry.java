package org.hormigas.ws.ports.watermark;

import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domain.watremark.Watermark;

import java.util.List;

public interface WatermarksRegistry {
    Uni<List<Watermark>> getWatermarks(int limit);
    Uni<Void> addWatermark(Watermark watermark);
}
