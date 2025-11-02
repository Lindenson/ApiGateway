package org.hormigas.ws.backpressure;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.hormigas.ws.backpressure.incomming.IncomingPublisherFactory;
import org.hormigas.ws.backpressure.outgoing.OutgoingPublisherFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public interface PublisherFactory<T, M extends PublisherMetrics, S> {
    PublisherFactory<T, M, S> withSink(Function<T, S> processor);
    PublisherFactory<T, M, S> withEmitter(AtomicReference<MultiEmitter<? super T>> emitter);
    PublisherFactory<T, M, S> withMetrics(M metrics);
    PublisherFactory<T, M, S> withMode(Mode mode);
    PublisherFactory<T, M, S> withQueueSizeCounter(AtomicInteger queueSizeContainer);
    Multi<Void> build();

    enum Mode {PARALLEL, SEQUENTIAL}

    final class PublisherFactories {

        private PublisherFactories() {} // нельзя инстанцировать

        @SuppressWarnings("unchecked")
        public static <T, M extends PublisherMetrics, S> PublisherFactory<T, M, S> getFactoryFor(String name) {
            return switch (name) {
                case "outgoing" -> (PublisherFactory<T, M, S>) new OutgoingPublisherFactory();
                case "incoming" -> (PublisherFactory<T, M, S>) new IncomingPublisherFactory();
                default -> throw new IllegalStateException("Unexpected value: " + name);
            };
        }
    }

}
