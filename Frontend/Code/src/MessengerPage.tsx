import React, { useState } from 'react';
import { useKeycloak } from '@react-keycloak/web';
import { Sidebar } from './Sidebar';
import { ChatHeader } from './ChatHeader';
import { ChatMessages } from './ChatMessages';
import { ChatInput } from './ChatInput';
import { useMessengerSocket } from './hooks/useMessengerSocket';

export const MessengerPage: React.FC = () => {
    const { keycloak } = useKeycloak();
    const [messageText, setMessageText] = useState('');
    const [activeChat, setActiveChat] = useState<string | null>(null);
    const [recipientId, setRecipientId] = useState('');

    const { conversations, presence, sendMessage } = useMessengerSocket(
        keycloak.token,
        keycloak.subject
    );

    const currentMessages = activeChat ? conversations[activeChat] || [] : [];

    const getUserName = (id: string) => presence.find(p => p.id === id)?.name || id;

    return (
        <div style={{ display: 'flex', height: '50vh', backgroundColor: '#f4f6f8', fontFamily: 'system-ui, sans-serif' }}>
            <Sidebar
                presence={presence}
                activeChat={activeChat}
                currentUserId={keycloak.subject || ''}
                onSelectUser={id => {
                    setRecipientId(id);
                    setActiveChat(id);
                }}
            />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <ChatHeader activeChat={activeChat} getUserName={getUserName} />
                {activeChat && <ChatMessages messages={currentMessages} />}
                {activeChat && (
                    <ChatInput
                        messageText={messageText}
                        onChange={setMessageText}
                        onSend={() => {
                            if (!recipientId || !messageText.trim()) return;
                            sendMessage(recipientId, messageText);
                            setMessageText('');
                        }}
                    />
                )}
            </div>
        </div>
    );
};

export default MessengerPage;
