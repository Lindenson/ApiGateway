package org.hormigas.ws.publisher.api;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.publisher.AcknolagePublisherFactory;
import org.hormigas.ws.publisher.MessagePublisherFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public interface PublisherFactory<T, M extends PublisherMetrics, S> {
    PublisherFactory<T, M, S> withProcessor(Function<T, S> processor);
    PublisherFactory<T, M, S> withEmitter(AtomicReference<MultiEmitter<? super T>> emitter);
    PublisherFactory<T, M, S> withMetrics(M metrics);
    PublisherFactory<T, M, S> withQueueSizeCounter(AtomicInteger queueSizeContainer);
    Multi<Void> build();


    final class PublisherFactories {

        private PublisherFactories() {} // нельзя инстанцировать

        @SuppressWarnings("unchecked")
        public static <T, M extends PublisherMetrics, S> PublisherFactory<T, M, S> getFactoryFor(String name) {
            return switch (name) {
                case "message" -> (PublisherFactory<T, M, S>) new MessagePublisherFactory();
                case "acknowledge" -> (PublisherFactory<T, M, S>) new AcknolagePublisherFactory();
                default -> throw new IllegalStateException("Unexpected value: " + name);
            };
        }
    }

}
