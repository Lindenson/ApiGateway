import React, { useEffect, useState } from 'react';
import { useKeycloak } from '@react-keycloak/web';
import axios from 'axios';

const httpsClient = axios.create({
    baseURL: 'https://nginx',
    withCredentials: true,
});

const ProtectedPage: React.FC = () => {
    const { keycloak } = useKeycloak();
    const [response, setResponse] = useState<string>('Запрос не выполнен');

    useEffect(() => {
        if (keycloak?.token && keycloak?.tokenParsed) {
            const roles: string[] = keycloak.tokenParsed.realm_access?.roles || [];

            let endpoint = '/fallout';
            if (roles.includes('client')) {
                endpoint = '/client/hello';
            } else if (roles.includes('master')) {
                endpoint = '/master/hello';
            }

            httpsClient
                .get(endpoint, {
                    headers: {
                        Authorization: `Bearer ${keycloak.token}`,
                    },
                })
                .then((res) => setResponse(res.data))
                .catch((err) => {
                    console.error(err);
                    setResponse(`Ошибка: ${err.message}`);
                });
        }
    }, [keycloak]);

    return (
        <div>
            <h2>Защищённый запрос к API:</h2>
            <pre className="response">{response}</pre>
        </div>
    );
};

export default ProtectedPage;
