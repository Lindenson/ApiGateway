package org.hormigas.ws.ports.channel.ws.transformer;

import org.hormigas.ws.ports.channel.presense.dto.ClientSession;

public interface Transformer<T, M> {
    T apply(T m, ClientSession<M> w);
}
