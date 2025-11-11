import React from 'react';

type ChatHeaderProps = {
    activeChat: string | null;
    getUserName: (id: string) => string;
};

export const ChatHeader: React.FC<ChatHeaderProps> = ({ activeChat, getUserName }) => (
    <div style={{ padding: 10, borderBottom: '1px solid #ddd', backgroundColor: '#fff' }}>
        <h3 style={{ margin: 0 }}>
            {activeChat ? `Чат с ${getUserName(activeChat)}` : '💬 Выберите пользователя для начала диалога'}
        </h3>
    </div>
);
