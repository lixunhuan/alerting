FROM registry.vizion.ai/es/base.7.2.0.latest
WORKDIR /usr/share/elasticsearch
RUN mkdir /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./source/java.policy /usr/share/elasticsearch/config/
COPY ./source/jvm.options /usr/share/elasticsearch/config/jvm.options
COPY ./source/opendistro_alerting/*  /usr/share/elasticsearch/plugins/opendistro_alerting/
COPY ./notification/build/libs/alerting-notification-1.2.0.0.jar /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./core/build/libs/alerting-core-1.2.0.0.jar /usr/share/elasticsearch/plugins/opendistro_alerting
COPY ./alerting/build/distributions/opendistro_alerting-1.2.0.0-SNAPSHOT.jar /usr/share/elasticsearch/plugins/opendistro_alerting

RUN mkdir /usr/share/elasticsearch/config/certs
COPY ./elastic-stack-ca.p12 /usr/share/elasticsearch/config/certs/elastic-stack-ca.p12
COPY ./elastic-certificates.p12 /usr/share/elasticsearch/config/certs/elastic-certificates.p12
RUN chown elasticsearch:elasticsearch /usr/share/elasticsearch/config/certs/elastic-stack-ca.p12 &chown elasticsearch:elasticsearch /usr/share/elasticsearch/config/certs/elastic-certificates.p12
RUN ls -ll /usr/share/elasticsearch/config/certs
