package org.hormigas.ws.core.watermark;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.watermark.dto.Watermark;

@Slf4j
@ApplicationScoped
public class OutboxLeaveStamp implements LeaveStamp {

    @Inject
    WatermarksRegistry watermarksRegistry;

    @Override
    public Uni<Void> setLeaveStamp(String clientId, long timeStamp) {
        log.info("Publishing leave stamp for client {}", clientId);
        return watermarksRegistry.addWatermark(new Watermark(clientId, timeStamp));
    }
}
