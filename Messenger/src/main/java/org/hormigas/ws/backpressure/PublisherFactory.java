package org.hormigas.ws.backpressure.api;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.backpressure.IncommingPublisherFactory;
import org.hormigas.ws.backpressure.OutgoingPublisherFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public interface PublisherFactory<T, M extends PublisherMetrics, S, E> {
    PublisherFactory<T, M, S, E> withProcessor(Function<E, S> processor);
    PublisherFactory<T, M, S, E> withEmitter(AtomicReference<MultiEmitter<? super T>> emitter);
    PublisherFactory<T, M, S, E> withMetrics(M metrics);
    PublisherFactory<T, M, S, E> withQueueSizeCounter(AtomicInteger queueSizeContainer);
    Multi<Void> build();


    final class PublisherFactories {

        private PublisherFactories() {} // нельзя инстанцировать

        @SuppressWarnings("unchecked")
        public static <T, M extends PublisherMetrics, S, E> PublisherFactory<T, M, S, E> getFactoryFor(String name) {
            return switch (name) {
                case "outgoing" -> (PublisherFactory<T, M, S, E>) new OutgoingPublisherFactory();
                case "incoming" -> (PublisherFactory<T, M, S, E>) new IncommingPublisherFactory();
                default -> throw new IllegalStateException("Unexpected value: " + name);
            };
        }
    }

}
