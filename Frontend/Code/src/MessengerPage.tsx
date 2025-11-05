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

const MessengerPage: React.FC = () => {
  const [conversations, setConversations] = useState<Record<string, ChatMessage[]>>({});
  const [messageText, setMessageText] = useState('');
  const [recipientId, setRecipientId] = useState('');
  const [activeChat, setActiveChat] = useState<string | null>(null);
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

        if (data.type === 'CHAT_ACK' && data.correlationId) {
          markAcknowledged(data.correlationId);
          return;
        }

        const peerId =
          data.senderId === keycloak.subject ? data.recipientId : data.senderId;

        // Add message
        addMessage(peerId, {
          id: data.messageId,
          text: data.payload.body,
          direction: data.senderId === keycloak.subject ? 'out' : 'in',
          acknowledged: data.senderId === keycloak.subject ? false : undefined,
          peerId,
        });

        // Create ACK for incoming messages
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

  return (
    <div style={{ padding: 20 }}>
      <h2>Messenger</h2>

      {/* Tabs for chats */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
        {Object.keys(conversations).map(peerId => (
          <div
            key={peerId}
            onClick={() => {setActiveChat(peerId); setRecipientId(peerId);}}
            style={{
              padding: '6px 10px',
              borderRadius: 8,
              backgroundColor: activeChat === peerId ? '#0a84ff' : '#ddd',
              color: activeChat === peerId ? '#fff' : '#000',
              cursor: 'pointer',
            }}
          >
            {peerId}
          </div>
        ))}
      </div>

      {/* Chat area */}
      {activeChat ? (
        <>
          <div
            style={{
              border: '1px solid #ccc',
              padding: '8px',
              height: '400px',
              overflowY: 'auto',
              backgroundColor: '#f9f9f9',
              marginBottom: 10,
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

          <div style={{ display: 'flex', gap: '10px' }}>
            <input
              type="text"
              placeholder="Your message..."
              value={messageText}
              onChange={(e) => setMessageText(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
              style={{ flex: 1 }}
            />
            <button onClick={sendMessage}>Send</button>
          </div>
        </>
      ) : (
        <div style={{ color: '#888' }}>💬 Выберите чат или начните новый диалог</div>
      )}

      {/* Add new chat */}
      <div style={{ marginTop: 10 }}>
        <input
          type="text"
          placeholder="Start new chat (Recipient ID)"
          value={recipientId}
          onChange={(e) => setRecipientId(e.target.value)}
          style={{ width: '60%' }}
        />
	<button
	  onClick={() => {
	    if (!recipientId.trim()) return;
	    setActiveChat(recipientId);
	    setConversations(prev => ({
	      ...prev,
	      [recipientId]: prev[recipientId] || [],
	    }));
	  }}
	>
	  Open
</button>
      </div>
    </div>
  );
};

export default MessengerPage;

