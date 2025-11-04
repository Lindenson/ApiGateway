package org.hormigas.ws.ports.channel.validator;

public interface ChannelValidator<T>{
    public boolean valid(T obj);
}
