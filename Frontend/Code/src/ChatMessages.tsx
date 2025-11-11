import React, { useEffect, useRef } from 'react';
import type { ChatMessage } from './Types.tsx';

type ChatMessagesProps = {
    messages: ChatMessage[];
};

export const ChatMessages: React.FC<ChatMessagesProps> = ({ messages }) => {
    const chatContainerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        const el = chatContainerRef.current;
        if (el) el.scrollTop = el.scrollHeight;
    }, [messages]);

    return (
        <div
            ref={chatContainerRef}
            style={{
                flex: 1,
                padding: 10,
                overflowY: 'auto',
                backgroundColor: '#eef1f4',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            {messages.map(m => (
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
    );
};
