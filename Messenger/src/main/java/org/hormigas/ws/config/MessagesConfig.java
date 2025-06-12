package org.hormigas.ws.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "processing.messages")
public interface MessagesConfig {

    Persistence persistence();

    Scheduler scheduler();

    interface Persistence {
        int batchSize();
        int queueSize();
        int timeoutMin();
    }

    interface Scheduler {
        int timeIntervalSec();
    }

}
