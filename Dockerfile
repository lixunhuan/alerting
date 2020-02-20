FROM registry.vizion.ai/es/base.7.4.2.latest
WORKDIR /usr/share/elasticsearch
RUN mkdir /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./source/java.policy /usr/share/elasticsearch/config/
COPY ./source/jvm.options /usr/share/elasticsearch/config/jvm.options
COPY ./source/opendistro_alerting/*  /usr/share/elasticsearch/plugins/opendistro_alerting/
COPY ./notification/build/libs/alerting-notification-1.4.0.0.jar /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./core/build/libs/alerting-core-1.4.0.0.jar /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./alerting/build/distributions/opendistro_alerting-1.4.0.0-SNAPSHOT.jar /usr/share/elasticsearch/plugins/opendistro_alerting
