#!/bin/sh

echo "⏳ Waiting for Keycloak to be ready..."

until curl http://keycloak:8080/realms/hormigas/.well-known/openid-configuration > /dev/null; do
  sleep 2
done

echo "✅ Keycloak is up, starting API Gateway..."
exec java -jar /app/api-gateway.jar
