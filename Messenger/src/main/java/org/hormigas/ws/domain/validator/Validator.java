package org.hormigas.ws.domain.validator;

public interface Validator<T>{
    boolean valid(T obj);
}
