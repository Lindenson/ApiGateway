package org.hormigas.ws.backpressure;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.Nonnull;
import org.hormigas.ws.backpressure.incomming.IncomingPublisherFactory;
import org.hormigas.ws.backpressure.outgoing.OutgoingPublisherFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.hormigas.ws.backpressure.PublisherFactory.Publisher.INCOMING;
import static org.hormigas.ws.backpressure.PublisherFactory.Publisher.OUTGOING;

public interface PublisherFactory<T, M extends PublisherMetrics, S> {
    PublisherFactory<T, M, S> withSink(Function<T, S> processor);
    PublisherFactory<T, M, S> withEmitter(AtomicReference<MultiEmitter<? super T>> emitter);
    PublisherFactory<T, M, S> withMetrics(M metrics);
    PublisherFactory<T, M, S> withMode(Mode mode);
    PublisherFactory<T, M, S> withQueueSizeCounter(AtomicInteger queueSizeContainer);
    Multi<Void> build();

    enum Mode {PARALLEL, SEQUENTIAL}
    enum Publisher {OUTGOING, INCOMING}


    final class PublisherFactories {

        private PublisherFactories() {} // нельзя инстанцировать

        @SuppressWarnings("unchecked")
        public static <T, M extends PublisherMetrics, S> PublisherFactory<T, M, S> getFactoryFor(@Nonnull Publisher name) {
            return switch (name) {
                case OUTGOING -> (PublisherFactory<T, M, S>) new OutgoingPublisherFactory();
                case INCOMING -> (PublisherFactory<T, M, S>) new IncomingPublisherFactory();
            };
        }
    }

}
