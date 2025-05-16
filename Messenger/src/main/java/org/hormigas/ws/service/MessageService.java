package org.hormigas.ws.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.repository.MessageRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class MessageService {

    private static int BATCH_SIZE = 100;
    private static int POSTPONE_MIN = 1;


    @Inject
    MessageRepository messageRepository;

    @WithTransaction
    public Uni<Long> removeAcknowledgedMessage(@NotNull String id) {
        Log.debug("Deleting message by id " + id);
        return messageRepository.delete("id", UUID.fromString(id));
    }

    @WithTransaction
    public Uni<List<Message>> getNextBatchToSend() {
        Log.debug("Trying fetch new batch of max size " + BATCH_SIZE);
        return messageRepository.findNextBatchToSend(BATCH_SIZE)
                .onItem().transformToUni(this::updateMessagesToNewTimeBatch);
    }

    @WithTransaction
    public Uni<List<Message>> updateMessagesToNewTimeBatch(@NotNull List<Message> messages) {
        LocalDateTime newSendAt = LocalDateTime.now().plusMinutes(POSTPONE_MIN);
        List<UUID> ids = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toList());
        Log.debug("Batch updating " + ids);
        if (ids.isEmpty()) return Uni.createFrom().item(messages);

        return messageRepository.updateSendAtForBatch(newSendAt, ids)
                .onItem().transform(updatedCount -> {
                    if (updatedCount > 0) {
                        Log.debug("Updated " + updatedCount);
                        messages.forEach(msg -> msg.setSendAt(newSendAt));
                        return messages;
                    } else {
                        Log.warn("Not updated!");
                        return Collections.emptyList();
                    }
                });
    }
}
