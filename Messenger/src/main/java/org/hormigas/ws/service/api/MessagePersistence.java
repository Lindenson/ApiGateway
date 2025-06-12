package org.hormigas.ws.service.api;

import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import org.hormigas.ws.domen.Message;

import java.util.List;

public interface MessagePersistence {
    Uni<Long> removeAcknowledgedMessage(@NotNull String id);
    Uni<List<Message>> getNextBatchToSend();
}
