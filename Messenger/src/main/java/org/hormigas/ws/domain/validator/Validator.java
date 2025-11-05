package org.hormigas.ws.domain.validator;

public interface Validator<T>{
    public boolean valid(T obj);
}
