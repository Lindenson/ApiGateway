package org.hormigas.ws.ports.channel.ws.filter;

import org.hormigas.ws.ports.channel.presense.dto.ClientSession;

public interface ChannelFilter<T, M>{
    boolean filter(T message, ClientSession<M> clientSession);
}
