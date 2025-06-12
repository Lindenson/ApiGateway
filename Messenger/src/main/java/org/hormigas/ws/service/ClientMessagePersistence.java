package org.hormigas.ws.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.config.MessagesConfig;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.repository.MessageRepository;
import org.hormigas.ws.service.api.MessagePersistence;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClientMessagePersistence implements MessagePersistence {

    @Inject
    MessagesConfig messagesConfig;

    @Inject
    MessageRepository messageRepository;

    @WithTransaction
    public Uni<Long> removeAcknowledgedMessage(@NotNull String id) {
        Log.debug("Deleting message by id " + id);
        return messageRepository.delete("id", UUID.fromString(id));
    }

    @WithTransaction
    public Uni<List<Message>> getNextBatchToSend() {
        Log.debug("Trying fetch new batch of max size " + messagesConfig.persistence().batchSize());
        return messageRepository.findNextBatchToSend(messagesConfig.persistence().batchSize())
                .onItem().transformToUni(this::updateMessagesToNewTimeBatch)
                .onFailure().recoverWithUni(Uni.createFrom().nullItem());
    }

    @WithTransaction
    public Uni<List<Message>> updateMessagesToNewTimeBatch(@NotNull List<Message> messages) {
        LocalDateTime newSendAt = LocalDateTime.now().plusMinutes(messagesConfig.persistence().timeoutMin());
        List<UUID> ids = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        if (ids.isEmpty()) return Uni.createFrom().item(messages);

        Log.debug("Batch updating " + ids);
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
