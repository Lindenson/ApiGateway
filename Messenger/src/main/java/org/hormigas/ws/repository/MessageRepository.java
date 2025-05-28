package org.hormigas.ws.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.logging.Log;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.domen.Status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MessageRepository implements PanacheRepository<Message> {

    public Uni<List<Message>> findNextBatchToSend(int limit) {
        Log.debug("findNextBatchToSend up to " + LocalDateTime.now());
        var query = find("sendAt <= ?1 and status = ?2", LocalDateTime.now(), Status.PENDING)
                .page(0, limit);
        return query.list();
    }

    public Uni<Message> findById(UUID id) {
        Log.debug("find message by Id: " + id);
        var query = find("id = ?1", id);
        return query.firstResult();
    }

    public Uni<Integer> updateSendAtById(@NotNull LocalDateTime newTime, @NotNull UUID id) {
        Log.debug("Updating date to " + LocalDateTime.now());
        return this.update("sendAt = ?1 WHERE id = ?2", newTime, id);
    }

    public Uni<Integer> updateSendAtForBatch(LocalDateTime newTime, List<UUID> ids) {
        Log.debug("Updating batch for ids " + ids);
        return this.update("sendAt = ?1 WHERE id IN ?2", newTime, ids);
    }
}
