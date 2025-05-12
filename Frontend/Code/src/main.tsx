import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import { ReactKeycloakProvider } from '@react-keycloak/web';
import keycloak from './keycloak';
import App from './App';
import './App.css';

const rootElement = document.getElementById('root');

if (rootElement) {
    createRoot(rootElement).render(
        <StrictMode>
            <ReactKeycloakProvider
                authClient={keycloak}
                initOptions={{
                    onLoad: 'login-required',
                    redirectUri: window.location.origin + "/#",
                }}
            >
                <App />
            </ReactKeycloakProvider>
        </StrictMode>,
    );
} else {
    console.error('Root element not found!');
}

