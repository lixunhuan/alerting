FROM registry.vizion.ai/es/base.7.2.0.latest
WORKDIR /usr/share/elasticsearch
RUN mkdir /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./source/java.policy /usr/share/elasticsearch/config/
COPY ./source/java.options /usr/share/elasticsearch/config/java.options
COPY ./source/opendistro_alerting/*  /usr/share/elasticsearch/plugins/opendistro_alerting/
COPY ./notification/build/libs/alerting-notification-1.2.0.0.jar /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./core/build/libs/alerting-core-1.2.0.0.jar /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./alerting/build/distributions/opendistro_alerting-1.2.0.0-SNAPSHOT.jar /usr/share/elasticsearch/plugins/opendistro_alerting

