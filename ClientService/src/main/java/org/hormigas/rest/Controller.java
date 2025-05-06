package org.example;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/hello")
public class Controller {

    @GET
    public String hello() {
        return "Привет от Клиента!";
    }
}

