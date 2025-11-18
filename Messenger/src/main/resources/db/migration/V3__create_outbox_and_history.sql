-- V3__create_outbox_and_history.sql

CREATE TABLE message_history (
                                 id BIGSERIAL PRIMARY KEY,
                                 message_id VARCHAR(128) NOT NULL, -- original client messageId (string)
                                 message_json JSONB NOT NULL,
                                 created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX idx_message_history_message_id ON message_history(message_id);

CREATE TABLE outbox (
                        id BIGSERIAL PRIMARY KEY,

    -- core message fields (subset of domain Message)
                        type            VARCHAR(64) NOT NULL,
                        sender_id       VARCHAR(128) NOT NULL,
                        recipient_id    VARCHAR(128) NOT NULL,
                        message_id      VARCHAR(128) NOT NULL,
                        correlation_id  VARCHAR(128),
                        sender_ts       BIGINT NOT NULL,
                        sender_tz       VARCHAR(64),
                        server_ts       BIGINT NOT NULL,

                        payload_json    JSONB NOT NULL,
                        meta_json       JSONB,

    -- outbox control fields
                        created_at      TIMESTAMPTZ DEFAULT now() NOT NULL,
                        lease_until     TIMESTAMPTZ,
                        processing_attempts INT DEFAULT 0 NOT NULL,
                        status          VARCHAR(32) DEFAULT 'PENDING' NOT NULL
);

CREATE INDEX idx_outbox_recipient_id ON outbox(recipient_id);
CREATE INDEX idx_outbox_status_lease ON outbox(status, lease_until);
CREATE INDEX idx_outbox_created_at ON outbox(created_at);