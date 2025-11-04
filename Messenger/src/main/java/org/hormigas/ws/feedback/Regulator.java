package org.hormigas.ws.feedback;

import java.time.Duration;

public interface Regulator {
    Duration getCurrentIntervalMs();
}
