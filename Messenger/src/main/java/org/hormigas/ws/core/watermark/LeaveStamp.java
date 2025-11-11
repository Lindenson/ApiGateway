package org.hormigas.ws.core.watermark;

import io.smallrye.mutiny.Uni;

public interface LeaveStamp {
    Uni<Void> setLeaveStamp(String clientId, long timestamp);
}
