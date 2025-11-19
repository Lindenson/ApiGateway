export type MessageDirection = 'in' | 'out' | 'system';

export interface BaseMessage {
    id: string;
    text: string;
    direction: MessageDirection;
    senderTimestamp: number;
}

export interface IncomingMessage extends BaseMessage {
    acknowledged?: undefined; // ack приходит только для исходящих
    peerId: string;
}

export interface OutgoingMessage extends BaseMessage {
    acknowledged: boolean;
    peerId: string;
}

export type ChatMessage = IncomingMessage | OutgoingMessage;

export interface ServerPayloadChat {
    kind: 'text';
    body: string;
}

export interface ServerPayloadAck {
    kind: 'text';
    body: string;
}

export type ServerPayload = ServerPayloadChat | ServerPayloadAck;

export interface ServerMessage {
    messageId: string;
    correlationId?: string;
    senderId: string;
    recipientId: string;
    conversationId: string;
    payload: ServerPayload;
    type: 'CHAT_IN' | 'CHAT_OUT' | 'CHAT_ACK' | 'PRESENT_INIT' | 'PRESENT_JOIN' | 'PRESENT_LEAVE';
    senderTimestamp: number;
    senderTimezone: string;
}

export type PresenceUser = { id: string; name?: string; };