import React, { useEffect, useState, useRef } from 'react';
import { useKeycloak } from '@react-keycloak/web';

type ServerMessage = {
    id: string;
    content: string;
};


const MessengerPage: React.FC = () => {
    const [messages, setMessages] = useState<string[]>([]);
    const wsRef = useRef<WebSocket | null>(null);
    const reconnectTimeoutRef = useRef<number | null>(null);
    const reconnectAttemptsRef = useRef(0);

    const { keycloak } = useKeycloak();

    useEffect(() => {
        if (!keycloak?.token) return;

        const connect = () => {
            const quarkusHeaderProtocol = encodeURIComponent(
                "quarkus-http-upgrade#Authorization#Bearer " + keycloak.token
             );
             const ws = new WebSocket("wss://nginx/messenger", [
                "bearer-token-carrier",
                 quarkusHeaderProtocol,
             ]);
            wsRef.current = ws;

            ws.onmessage = (event) => {
                const data: ServerMessage = JSON.parse(event.data);
                console.log("Received and acknowledged:", data.content);
                setMessages((prev) => [...prev, data.content]);
                ws.send(JSON.stringify({ ackId: data.id }));
            };

            ws.onerror = (err) => {
                console.error("WebSocket error:", err);
            };

            ws.onclose = (event) => {
                console.log("WebSocket connection closed", event.reason);
                if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);

                const timeout = Math.min(1000 * 2 ** reconnectAttemptsRef.current, 30000); // max 30 сек
                reconnectTimeoutRef.current = setTimeout(() => {
                    reconnectAttemptsRef.current++;
                    console.log(`Reconnecting... try #${reconnectAttemptsRef.current}`);
                    connect();
                }, timeout);
            };
        };

        reconnectAttemptsRef.current = 0;
        connect();

        return () => {
            if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
            if (wsRef.current) wsRef.current.close();
        };
    }, [keycloak]);

    return (
        <div>
            <h2>Сообщения с сервера</h2>
            <div
                style={{
                    border: '1px solid #ccc',
                    padding: '10px',
                    height: '300px',
                    overflowY: 'auto',
                    backgroundColor: '#f9f9f9',
                }}
            >
                {messages.length === 0 ? (
                    <p>Нет сообщений</p>
                ) : (
                    messages.map((msg, idx) => <div key={idx}>{msg}</div>)
                )}
            </div>
        </div>
    );
};

export default MessengerPage;
