import React from 'react';
import { useKeycloak } from '@react-keycloak/web';
import ProtectedPage from './ProtectedPage';
import MessengerPage from "./MessengerPage.tsx";


const App: React.FC = () => {
    const { keycloak, initialized } = useKeycloak();

    if (!initialized) return <div className="loading">Загрузка Keycloak...</div>;

    return (
        <div className="app-container">
            <img
                src="https://cdn-icons-png.flaticon.com/128/2959/2959309.png"
                alt="Ant"
                className="ant-image"
            />
            <h1 className="title">Hormigas React Client</h1>

            {keycloak.authenticated ? (
                <>
                    <p className="welcome">Добро пожаловать, <strong>{keycloak.tokenParsed?.preferred_username}</strong></p>
                    <button className="logout-button" onClick={() => keycloak.logout({ redirectUri: "https://nginx/#" })}>
                        Выйти
                    </button>
                    <ProtectedPage />
                    <MessengerPage />
                </>
            ) : (
                <button className="login-button" onClick={() => keycloak.login()}>
                    Войти
                </button>
            )}
        </div>
    );
};

export default App;
