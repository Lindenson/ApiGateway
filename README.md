# ApiGateway


## 🛡️ API Gateway for Hormigas Platform

### Описание
Этот проект представляет собой API Gateway, реализованный на основе Spring Cloud Gateway, выступающий в качестве единой точки входа для микросервисов платформы Hormigas. Он осуществляет:

- маршрутизацию запросов к микросервисам клиентов и мастеров;

- аутентификацию и авторизацию через Keycloak;

- балансировку нагрузки и отказоустойчивость;

- экспорт метрик в Prometheus;

- nginx как первая линия для диспетчиризации статического контента, трансфирмации https->http, предупреждения DDoS атак. 


## 🔧 Основные компоненты

### 🌐 Spring Cloud Gateway

Точка входа https://nginx.. (установите как localhost в /etc/hosts)

Маршруты определены для двух основных микросервисов:

- Clients Service: /client/hello -> отправляет на микросервис, который выдает приетсвтие на get запрос.

- Masters Service: /master/hello -> отправляет на микросервис, который выдает приетсвтие на get запрос.

Каждый маршрут:

- выполняет переписывание пути (RewritePath) сокращая URL, убирая часть "client" или "master", нужные только для маршрутизации;

- защищён фильтром JwtRole, проверяющим роль пользователя (master для master и client для client);

- обернут в Circuit Breaker (resilience4j), возвращающий fallback в случае ошибки;

- fallback может быть другой сервисом, кешированным или статическим ресурсом, или просто кодом ошибки.

### 🔐 OAuth2 с Keycloak
Gateway выступает в роли OAuth2-клиента:

- Используется authorization_code flow.

- Для  логина Keycloak перенаправляет пользователя на: https://nginx/login/oauth2/code/keycloak.

- После логина клиент получает токен и рефреш-токен;

- Для выхода клеинт должен перейти на /logout.

- Настройки связи с keycloak:

```
cloud-security:
  keycloak-realm: hormigas
  keycloakURL: https://nginx:8443
  gateway-redirectURL: https://nginx
```


Данные пользователя (в т.ч. роль) извлекаются через OpenID Connect (scope: openid, profile, email).

### 📊 Метрики и наблюдаемость
Метрики экспортируются в Prometheus (/actuator/prometheus).

Prometheus доступен по http://localhost:9090 для тестового режима.

Активация необходимых эндпоинтов: health, info, metrics, prometheus.

Включена микрометрика resilience4j и spring-cloud-loadbalancer.

Доступ к метрикам предоставлен только Prometheus, снаружи закрыт.

### ⚙️ Балансировка и Здоровье
Балансировка нагрузки производится через Spring Cloud LoadBalancer.

Поддержка health-check'ов с периодичностью 15 секунд для сервисов:

- masters: /q/health

- clients: /q/health

- Список хостов для конкретных сервисов, учавствующих в балансироваке нагрузки указан статически. Service discovery не используется hosts-lists:
```
hosts-lists:
  client-hosts: [...]
  master-hosts: [...]
```

### 🧱 Конфигурация NGINX
Nginx выполняет:

- TLS терминацию (самоподписанный сертификат для тестов);

- обратное проксирование запросов к API Gateway (https://nginx -> http://api-gateway:8080);

- ограничение скорости и количества соединений (anti-DDOS).


### 📡 Микросервисы

Микросервисы реализованы на Quarkus и пока функционируют лишь для демонстрации работы. Имеют простое API:
 - request GET /hello
 - response Привет от Клиета (Мастера)