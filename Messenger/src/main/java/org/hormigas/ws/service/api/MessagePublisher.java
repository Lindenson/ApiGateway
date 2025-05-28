package org.hormigas.ws.service.api;

import org.hormigas.ws.domen.Message;

public interface MessagePublisher {
    void publish(Message msg);
}
