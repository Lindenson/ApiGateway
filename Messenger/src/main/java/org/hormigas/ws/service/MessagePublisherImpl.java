package org.hormigas.ws.service;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hormigas.ws.domen.Message;
import org.hormigas.ws.service.api.MessagePublisher;
import org.hormigas.ws.websocket.api.MessengerWebSocket;

import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class MessagePublisherImpl implements MessagePublisher {

    private final UnicastProcessor<Message> processor = UnicastProcessor.create();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final int LIMIT = 1000_000;


    @Inject
    MessengerWebSocket messengerWebSocket;

    @PostConstruct
    void init() {
        processor
                .onOverflow().drop()
                .subscribe().with(
                        msg -> {
                            messengerWebSocket.sendToClient(msg)
                                    .subscribe().with(
                                            unused -> counter.decrementAndGet(),
                                            failure -> {
                                                counter.decrementAndGet();
                                                Log.error("Failed to send message", failure);
                                            });
                        },
                        failure -> {
                            Log.error("Processor terminated unexpectedly", failure);
                        },
                        () -> Log.debug("Processor completed")
                );
    }


    public void publish(Message msg) {
        if (counter.incrementAndGet() < LIMIT) {
            try {
                processor.onNext(msg);
            } catch (Throwable t) {
                counter.decrementAndGet();
                Log.error("Failed to publish message to processor", t);
            }
        } else {
            counter.decrementAndGet();
            Log.error("Message dropped due to limit");
        }
    }
}
