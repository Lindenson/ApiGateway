import React from 'react';

type ChatInputProps = {
    messageText: string;
    onChange: (value: string) => void;
    onSend: () => void;
};

export const ChatInput: React.FC<ChatInputProps> = ({ messageText, onChange, onSend }) => (
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
            onChange={e => onChange(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && onSend()}
            style={{
                flex: 1,
                border: '1px solid #ccc',
                borderRadius: 6,
                padding: '8px',
            }}
        />
        <button
            onClick={onSend}
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
);
