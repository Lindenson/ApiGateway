package org.hormigas.ws.domen;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Entity
public class Message {
    @Id
    UUID id;

    String clientId;
    String content;
    LocalDateTime sendAt;

    @Enumerated(EnumType.STRING)
    Status status;
}
