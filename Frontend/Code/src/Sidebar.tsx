import React from 'react';
import type { PresenceUser } from './Types.tsx';

type SidebarProps = {
    presence: PresenceUser[];
    activeChat: string | null;
    currentUserId: string;
    onSelectUser: (id: string) => void;
};

export const Sidebar: React.FC<SidebarProps> = ({
                                                    presence,
                                                    activeChat,
                                                    currentUserId,
                                                    onSelectUser,
                                                }) => {
    return (
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
                        onClick={() => p.id !== currentUserId && onSelectUser(p.id)}
                        style={{
                            padding: '8px 10px',
                            borderRadius: 6,
                            backgroundColor:
                                activeChat === p.id
                                    ? '#0a84ff'
                                    : p.id === currentUserId
                                        ? '#eee'
                                        : '#f9f9f9',
                            color:
                                activeChat === p.id
                                    ? '#fff'
                                    : p.id === currentUserId
                                        ? '#999'
                                        : '#000',
                            marginBottom: 6,
                            cursor: p.id === currentUserId ? 'not-allowed' : 'pointer',
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
    );
};
