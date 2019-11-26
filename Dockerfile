FROM alpine:3.6
WORKDIR /application
COPY ./notification/build/libs/alerting-notification-1.2.0.0.jar /application
COPY ./core/build/libs/alerting-core-1.2.0.0.jar /application
COPY ./alerting/build/distributions/opendistro_alerting-1.2.0.0-SNAPSHOT.jar /application

