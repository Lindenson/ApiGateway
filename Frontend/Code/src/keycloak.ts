import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
    url: 'https://nginx:8443/',
    realm: 'hormigas',
    clientId: 'api-gateway',
});

export default keycloak;