package org.hormigas.ws.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hormigas.ws.mother.MessageCreator.getMessage;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestTransaction
public class MessageServiceTest {

    @Inject
    MessagePersistenceService messagePersistenceService;

    @InjectMock
    MessageRepository messageRepository;

    @BeforeEach
    void setup() {
        when(messageRepository.persist(any(Message.class))).thenCallRealMethod();
        when(messageRepository.updateSendAtForBatch(any(LocalDateTime.class), anyList())).thenCallRealMethod();
        when(messageRepository.findNextBatchToSend(anyInt())).thenCallRealMethod();
        when(messageRepository.findById(any(UUID.class))).thenCallRealMethod();
        when(messageRepository.find(anyString(), any(Object.class))).thenCallRealMethod();
        when(messageRepository.update(anyString(), ArgumentMatchers.<Object[]>any())).thenCallRealMethod();
        when(messageRepository.delete(anyString(), ArgumentMatchers.<Object[]>any())).thenCallRealMethod();
    }

    @Test
    @RunOnVertxContext
    void testRemoveAcknowledgedMessage_commitsOnSuccess(TransactionalUniAsserter asserter) {
        Message message = getMessage();
        asserter.execute(() -> messageRepository.persist(message));
        asserter.assertNotNull(() -> messageRepository.findById(message.getId()));
        asserter.execute(() -> messagePersistenceService.removeAcknowledgedMessage(message.getId().toString()));
        asserter.assertNull(() -> messageRepository.findById(message.getId()));
    }

    @Test
    @RunOnVertxContext
    void testUpdateMessagesToNewTimeBatch_success(TransactionalUniAsserter asserter) {
        Message message = getMessage();

        asserter.execute(() -> messageRepository.persist(message));
        asserter.assertNotNull(() -> messageRepository.findById(message.getId()));

        asserter.execute(() -> messagePersistenceService.getNextBatchToSend());

        asserter.assertNotNull(() -> messageRepository.findById(message.getId()));
        asserter.assertThat(
                () -> messageRepository.findById(message.getId()),
                found -> assertThat(found.getSendAt().truncatedTo(ChronoUnit.MILLIS))
                        .isNotEqualTo(message.getSendAt().truncatedTo(ChronoUnit.MILLIS))
        );
    }

    @Test
    @RunOnVertxContext
    void testUpdateMessagesToNewTimeBatch_rollsBackOnFailure(TransactionalUniAsserter asserter) {
        Message message = getMessage();
        when(messageRepository.find(anyString(), ArgumentMatchers.<Object[]>any())).thenCallRealMethod();
        when(messageRepository.update(any(String.class), any(Object.class))).thenThrow(RuntimeException.class);

        asserter.execute(() -> messageRepository.persist(message));
        asserter.assertNotNull(() -> messageRepository.findById(message.getId()));

        asserter.execute(() -> messagePersistenceService.getNextBatchToSend());
        asserter.assertNotNull(() -> messageRepository.findById(message.getId()));

        asserter.assertThat(
                () -> messageRepository.findById(message.getId()),
                found -> assertThat(found.getSendAt().truncatedTo(ChronoUnit.MILLIS))
                        .isEqualTo(message.getSendAt().truncatedTo(ChronoUnit.MILLIS))
        );
    }
}
