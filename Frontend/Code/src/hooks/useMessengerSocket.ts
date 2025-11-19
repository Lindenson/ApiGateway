import { useEffect, useRef, useState, useCallback } from 'react';
import type {ChatMessage, PresenceUser, ServerMessage} from "../Types.tsx";

// -------------------- Фабрика сообщений --------------------
function createChatMessage(msg: ServerMessage, currentUserId: string): ChatMessage {
    const isOut = msg.senderId === currentUserId;
    return {
        id: msg.messageId,
        text: msg.payload.body,
        direction: isOut ? 'out' : 'in',
        acknowledged: isOut ? true : undefined,
        peerId: isOut ? msg.recipientId : msg.senderId,
        senderTimestamp: msg.senderTimestamp,
    };
}

// -------------------- Хук --------------------
export const useMessengerSocket = (token?: string, currentUserId?: string) => {
    const [conversations, setConversations] = useState<Record<string, ChatMessage[]>>({});
    const [presence, setPresence] = useState<PresenceUser[]>([]);
    const wsRef = useRef<WebSocket | null>(null);
    const reconnectTimerRef = useRef<NodeJS.Timeout | null>(null);
    const reconnectAttemptsRef = useRef(0);

    // --- Дедупликация и добавление сообщения ---
    const addMessage = useCallback((peerId: string, msg: ChatMessage) => {
        setConversations(prev => {
            const existing = prev[peerId] || [];
            const map = new Map<string, ChatMessage>();
            existing.forEach(m => map.set(m.id, m));
            map.set(msg.id, msg);

            const sorted = Array.from(map.values())
                .sort((a, b) => a.senderTimestamp - b.senderTimestamp)
                .slice(-50);

            return { ...prev, [peerId]: sorted };
        });
    }, []);

    // --- Пометка ACK для исходящих сообщений ---
    const markAcknowledged = useCallback((correlationId: string) => {
        setConversations(prev => {
            const updated: Record<string, ChatMessage[]> = {};
            for (const peerId of Object.keys(prev)) {
                updated[peerId] = prev[peerId].map(m =>
                    m.id === correlationId ? { ...m, acknowledged: true } : m
                );
            }
            return updated;
        });
    }, []);

    // --- Отправка сообщения ---
    const sendMessage = useCallback((recipientId: string, text: string) => {
        if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN || !currentUserId) return;

        const msgId = crypto.randomUUID();
        const senderTimestamp = Date.now();

        const msg: ServerMessage = {
            messageId: msgId,
            senderId: currentUserId,
            recipientId,
            type: 'CHAT_IN',
            senderTimestamp,
            senderTimezone: 'Europe/Madrid',
            conversationId: 'new chat',
            payload: { kind: 'text', body: text },
        } as ServerMessage;

        wsRef.current.send(JSON.stringify(msg));

        addMessage(recipientId, {
            id: msgId,
            text,
            direction: 'out',
            acknowledged: false,
            peerId: recipientId,
            senderTimestamp: senderTimestamp,
        });
    }, [addMessage, currentUserId]);

    // --- WebSocket подключение ---
    useEffect(() => {
        if (!token || !currentUserId) return;

        const connect = () => {
            const quarkusHeaderProtocol = encodeURIComponent(
                'quarkus-http-upgrade#Authorization#Bearer ' + token.trim()
            );

            const ws = new WebSocket('wss://nginx/messenger', ['bearer-token-carrier', quarkusHeaderProtocol]);
            wsRef.current = ws;

            ws.onopen = () => {
                console.log('✅ WebSocket connected');
                reconnectAttemptsRef.current = 0;
                if (reconnectTimerRef.current) {
                    clearTimeout(reconnectTimerRef.current);
                    reconnectTimerRef.current = null;
                }

                // --- Fetch presence ---
                fetch(`${window.location.origin}/chat/api/presence`, {
                    headers: { 'Authorization': `Bearer ${token.trim()}` },
                })
                    .then(res => res.ok ? res.json() : Promise.reject(res.status))
                    .then((data: PresenceUser[]) => {
                        const unique = Array.from(new Map(data.map(u => [u.id, u])).values())
                            .sort((a, b) => a.name?.localeCompare(b.name ?? '') ?? 0);
                        setPresence(unique);
                    })
                    .catch(err => console.error('⚠️ Presence fetch failed:', err));

                // --- Fetch chat history ---
                fetch(`${window.location.origin}/chat/api/history`, {
                    headers: { 'Authorization': `Bearer ${token.trim()}` },
                })
                    .then(res => res.ok ? res.json() : Promise.reject(res.status))
                    .then((history: ServerMessage[]) => {
                        const map: Record<string, Map<string, ChatMessage>> = {};
                        history.forEach(msg => {
                            if (msg.type !== 'CHAT_IN' && msg.type !== 'CHAT_OUT') return;
                            const peerId = msg.senderId === currentUserId ? msg.recipientId : msg.senderId;
                            if (!peerId) return;
                            if (!map[peerId]) map[peerId] = new Map();
                            map[peerId].set(msg.messageId, createChatMessage(msg, currentUserId));
                        });

                        const result: Record<string, ChatMessage[]> = {};
                        for (const peerId of Object.keys(map)) {
                            result[peerId] = Array.from(map[peerId].values())
                                .sort((a, b) => a.senderTimestamp - b.senderTimestamp)
                                .slice(-50);
                        }
                        setConversations(result);
                    })
                    .catch(err => console.error('⚠️ History fetch failed:', err));
            };

            ws.onclose = (e) => {
                console.warn(`❌ WS closed: code=${e.code}, reason=${e.reason}`);
                scheduleReconnect();
            };

            ws.onerror = (err) => {
                console.error('⚠️ WS error', err);
                ws.close();
            };

            ws.onmessage = (event) => {
                const data: ServerMessage = JSON.parse(event.data);

                // --- Presence ---
                if (data.type === 'PRESENT_INIT' || data.type === 'PRESENT_JOIN') {
                    try {
                        const users = data.type === 'PRESENT_INIT' ? JSON.parse(data.payload.body) : [JSON.parse(data.payload.body)];
                        const unique = Array.from(new Map(users.map(u => [u.id, u])).values())
                            .sort((a, b) => a.name?.localeCompare(b.name ?? '') ?? 0);

                        setPresence(prev => {
                            const map = new Map(prev.map(p => [p.id, p]));
                            unique.forEach(u => map.set(u.id, u));
                            return Array.from(map.values()).sort((a, b) => a.name?.localeCompare(b.name ?? '') ?? 0);
                        });
                    } catch { console.warn('Bad presence payload'); }
                    return;
                }

                if (data.type === 'PRESENT_LEAVE') {
                    try {
                        const user = JSON.parse(data.payload.body) as PresenceUser;
                        setPresence(prev => prev.filter(p => p.id !== user.id));
                    } catch { console.warn('Bad presence leave payload'); }
                    return;
                }

                // --- ACK ---
                if (data.type === 'CHAT_ACK' && data.correlationId) {
                    markAcknowledged(data.correlationId);
                    return;
                }

                // --- Send ACK ---
                if (data.recipientId === currentUserId && wsRef.current) {
                    wsRef.current.send(JSON.stringify({
                        messageId: crypto.randomUUID(),
                        correlationId: data.messageId,
                        type: 'CHAT_ACK',
                        senderId: data.recipientId,
                        recipientId: data.senderId,
                        senderTimestamp: Date.now(),
                        payload: { kind: 'text', body: `Ack for message ${data.messageId}` },
                    }));
                }

                // --- Chat messages ---
                if (data.payload.kind !== 'text') return;
                const peerId = data.senderId === currentUserId ? data.recipientId : data.senderId;
                addMessage(peerId, createChatMessage(data, currentUserId));
            };
        };

        const scheduleReconnect = () => {
            if (reconnectTimerRef.current) return;
            const attempt = ++reconnectAttemptsRef.current;
            const delay = Math.min(1000 * 2 ** (attempt - 1), 30000);
            console.log(`🔁 Reconnecting in ${delay / 1000}s (attempt ${attempt})...`);
            reconnectTimerRef.current = setTimeout(() => {
                reconnectTimerRef.current = null;
                connect();
            }, delay);
        };

        connect();

        return () => {
            if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
            wsRef.current?.close();
        };
    }, [token, currentUserId, addMessage, markAcknowledged]);

    return { conversations, presence, sendMessage };
};
