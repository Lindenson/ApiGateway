import React, { useEffect, useState, useRef } from 'react';
import { useKeycloak } from '@react-keycloak/web';


const MessengerPage: React.FC = () => {
    const [messages, setMessages] = useState<string[]>([]);
    const wsRef = useRef<WebSocket | null>(null);

    const { keycloak } = useKeycloak();

    useEffect(() => {
        if (keycloak?.token) {
            const quarkusHeaderProtocol = encodeURIComponent("quarkus-http-upgrade#Authorization#Bearer " + keycloak.token)
            const ws = new WebSocket('wss://nginx/messenger', ["bearer-token-carrier", quarkusHeaderProtocol]);
            wsRef.current = ws;

            ws.onmessage = (event) => {
                setMessages((prev) => [...prev, event.data]);
            };

            ws.onerror = (err) => {
                console.error('WebSocket error:', err);
            };

            ws.onclose = () => {
                console.log('WebSocket connection closed');
            };

            return () => {
                ws.close();
            };
        }
    }, []);

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
