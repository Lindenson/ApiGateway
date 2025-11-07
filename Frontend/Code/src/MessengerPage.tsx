import React, { useEffect, useState, useRef } from 'react';
import { useKeycloak } from '@react-keycloak/web';

type ServerMessage = {
  messageId: string;
  correlationId?: string;
  senderId: string;
  recipientId: string;
  payload: { body: string; kind: string };
  type: string;
};

type ChatMessage = {
  id: string;
  text: string;
  direction: 'in' | 'out' | 'system';
  acknowledged?: boolean;
  peerId?: string;
};

type PresenceUser = {
  id: string;
  name?: string;
};

const MessengerPage: React.FC = () => {
  const [conversations, setConversations] = useState<Record<string, ChatMessage[]>>({});
  const [messageText, setMessageText] = useState('');
  const [recipientId, setRecipientId] = useState('');
  const [activeChat, setActiveChat] = useState<string | null>(null);
  const [presence, setPresence] = useState<PresenceUser[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const { keycloak } = useKeycloak();

  const addMessage = (peerId: string, msg: ChatMessage) => {
    setConversations(prev => ({
      ...prev,
      [peerId]: [...(prev[peerId] || []), msg].slice(-50),
    }));
  };

  const markAcknowledged = (correlationId: string) => {
    setConversations(prev => {
      const updated = { ...prev };
      for (const peerId of Object.keys(updated)) {
        updated[peerId] = updated[peerId].map(m =>
            m.id === correlationId ? { ...m, acknowledged: true } : m
        );
      }
      return updated;
    });
  };

  useEffect(() => {
    if (!keycloak?.token) return;

    const connect = () => {
      const quarkusHeaderProtocol = encodeURIComponent(
          'quarkus-http-upgrade#Authorization#Bearer ' + keycloak.token
      );

      const ws = new WebSocket('wss://nginx/messenger', [
        'bearer-token-carrier',
        quarkusHeaderProtocol,
      ]);
      wsRef.current = ws;

      ws.onmessage = (event) => {
        const data: ServerMessage = JSON.parse(event.data);

        // --- 🟢 PRESENCE ---
        if (data.type === 'PRESENT_INIT') {
          try {
            const list = JSON.parse(data.payload.body) as PresenceUser[];
            setPresence(list);
          } catch {
            console.warn('Bad presence init payload');
          }
          return;
        }

        if (data.type === 'PRESENT_JOIN') {
          try {
            const user = JSON.parse(data.payload.body) as PresenceUser;
            setPresence(prev =>
                prev.some(p => p.id === user.id) ? prev : [...prev, user]
            );
          } catch {
            console.warn('Bad presence join payload');
          }
          return;
        }

        if (data.type === 'PRESENT_LEAVE') {
          try {
            const user = JSON.parse(data.payload.body) as PresenceUser;
            setPresence(prev => prev.filter(p => p.id !== user.id));
          } catch {
            console.warn('Bad presence leave payload');
          }
          return;
        }

        // --- ✅ ACK ---
        if (data.type === 'CHAT_ACK' && data.correlationId) {
          markAcknowledged(data.correlationId);
          return;
        }

        // --- 💬 CHAT ---
        const peerId =
            data.senderId === keycloak.subject ? data.recipientId : data.senderId;

        addMessage(peerId, {
          id: data.messageId,
          text: data.payload.body,
          direction: data.senderId === keycloak.subject ? 'out' : 'in',
          acknowledged: data.senderId === keycloak.subject ? false : undefined,
          peerId,
        });

        if (data.recipientId === keycloak.subject) {
          const ack = {
            messageId: crypto.randomUUID(),
            correlationId: data.messageId,
            type: 'CHAT_ACK',
            senderId: data.recipientId,
            recipientId: data.senderId,
            clientTimestamp: Date.now(),
            requiresAck: false,
            durable: false,
            persistent: false,
            payload: { kind: 'ack', body: `Ack for message ${data.messageId}` },
          };
          ws.send(JSON.stringify(ack));
        }
      };

      ws.onopen = () => console.log('✅ WebSocket connected');
      ws.onclose = () => console.log('❌ WebSocket closed');
    };

    connect();
    return () => wsRef.current?.close();
  }, [keycloak]);

  const sendMessage = () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
    if (!recipientId || !messageText.trim()) return;

    const msgId = crypto.randomUUID();

    const msg = {
      messageId: msgId,
      senderId: keycloak.subject,
      recipientId,
      type: 'CHAT_IN',
      clientTimestamp: Date.now(),
      requiresAck: true,
      durable: false,
      persistent: false,
      payload: { kind: 'chat', body: messageText },
    };

    wsRef.current.send(JSON.stringify(msg));

    addMessage(recipientId, {
      id: msgId,
      text: messageText,
      direction: 'out',
      acknowledged: false,
      peerId: recipientId,
    });

    setMessageText('');
    setActiveChat(recipientId);
  };

  const currentMessages = activeChat ? conversations[activeChat] || [] : [];

  const getUserName = (id: string) => {
    const u = presence.find(p => p.id === id);
    return u?.name || id;
  };

  return (
      <div
          style={{
            display: 'flex',
            height: '60vh',
            backgroundColor: '#f4f6f8',
            fontFamily: 'system-ui, sans-serif',
          }}
      >
        {/* 🟢 Sidebar with online users */}
        <div
            style={{
              width: '250px',
              backgroundColor: '#fff',
              borderRight: '1px solid #ddd',
              padding: '10px',
              overflowY: 'auto',
            }}
        >
          <h3 style={{ margin: '10px 0' }}>🟢 Онлайн ({presence.length})</h3>
          {presence.length ? (
              presence.map(p => (
                  <div
                      key={p.id}
                      onClick={() => {
                        if (p.id === keycloak.subject) return;
                        setRecipientId(p.id);
                        setActiveChat(p.id);
                        setConversations(prev => ({
                          ...prev,
                          [p.id]: prev[p.id] || [],
                        }));
                      }}
                      style={{
                        padding: '8px 10px',
                        borderRadius: 6,
                        backgroundColor:
                            activeChat === p.id
                                ? '#0a84ff'
                                : p.id === keycloak.subject
                                    ? '#eee'
                                    : '#f9f9f9',
                        color:
                            activeChat === p.id
                                ? '#fff'
                                : p.id === keycloak.subject
                                    ? '#999'
                                    : '#000',
                        marginBottom: 6,
                        cursor:
                            p.id === keycloak.subject ? 'not-allowed' : 'pointer',
                        transition: 'all 0.2s',
                      }}
                  >
                    {p.name || p.id}
                  </div>
              ))
          ) : (
              <p style={{ color: '#888' }}>Никого онлайн</p>
          )}
        </div>

        {/* 💬 Main chat area */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: 10, borderBottom: '1px solid #ddd', backgroundColor: '#fff' }}>
            <h3 style={{ margin: 0 }}>
              {activeChat
                  ? `Чат с ${getUserName(activeChat)}`
                  : '💬 Выберите пользователя для начала диалога'}
            </h3>
          </div>

          {activeChat && (
              <div
                  style={{
                    flex: 1,
                    padding: 10,
                    overflowY: 'auto',
                    backgroundColor: '#eef1f4',
                  }}
              >
                {currentMessages.map(m => (
                    <div
                        key={m.id}
                        style={{
                          textAlign: m.direction === 'out' ? 'right' : 'left',
                          margin: '5px 0',
                        }}
                    >
                <span
                    style={{
                      display: 'inline-block',
                      backgroundColor: m.direction === 'out' ? '#dcf8c6' : '#fff',
                      borderRadius: 10,
                      padding: '6px 10px',
                      maxWidth: '70%',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.15)',
                    }}
                >
                  {m.text}
                  {m.direction === 'out' && (
                      <span
                          style={{
                            marginLeft: 6,
                            fontSize: 12,
                            color: m.acknowledged ? '#0a84ff' : '#999',
                          }}
                      >
                      {m.acknowledged ? '✓✓' : '✓'}
                    </span>
                  )}
                </span>
                    </div>
                ))}
              </div>
          )}

          {activeChat && (
              <div
                  style={{
                    display: 'flex',
                    gap: '10px',
                    padding: 10,
                    borderTop: '1px solid #ddd',
                    backgroundColor: '#fff',
                  }}
              >
                <input
                    type="text"
                    placeholder="Введите сообщение..."
                    value={messageText}
                    onChange={(e) => setMessageText(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                    style={{
                      flex: 1,
                      border: '1px solid #ccc',
                      borderRadius: 6,
                      padding: '8px',
                    }}
                />
                <button
                    onClick={sendMessage}
                    style={{
                      backgroundColor: '#0a84ff',
                      color: 'white',
                      border: 'none',
                      borderRadius: 6,
                      padding: '8px 14px',
                      cursor: 'pointer',
                    }}
                >
                  Отправить
                </button>
              </div>
          )}
        </div>
      </div>
  );
};

export default MessengerPage;
