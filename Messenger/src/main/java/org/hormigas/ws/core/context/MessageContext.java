package org.hormigas.ws.core.router.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageContext<T> {
    private final T payload;
    private boolean delivered;
    private boolean persisted;
    private boolean cached;
    private Throwable error;

    public boolean hasError() {
        return error != null;
    }
}
