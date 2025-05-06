FROM registry.access.redhat.com/ubi9/openjdk-21:1.21

COPY wait-for-keycloak.sh /wait-for-keycloak.sh
COPY  ./target/api-gateway-0.0.1.jar /app/api-gateway.jar
COPY ./certs/selfsigned.crt /tmp/selfsigned.crt

USER root

RUN chmod +x /wait-for-keycloak.sh

RUN keytool -import -trustcacerts \
    -file /tmp/selfsigned.crt \
    -alias nginx \
    -keystore /usr/lib/jvm/java-21-openjdk/lib/security/cacerts \
    -storepass changeit -noprompt

USER 1000

EXPOSE 8080

ENTRYPOINT ["/wait-for-keycloak.sh"]
