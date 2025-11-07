package org.hormigas.ws.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "processing.messages")
public interface MessagesConfig {

    //toDo навсти порядок

    Outbox outbox();
    Scheduler scheduler();
    Feedback feedback();
    ChannelRetry channelRetry();

    interface Outbox {
        int batchSize();
        int sendingQueueSize();
        int incomingQueueSize();
    }

    interface Scheduler {
        String timeIntervalMs();
    }

    interface Feedback {
        int baseIntervalMs();
        double adjustmentFactor();
        double recoveryFactor();
    }

    interface ChannelRetry {
        boolean retry();
        int minBackoffMs();
        int maxBackoffMs();
        int maxRetries();
    }

}
