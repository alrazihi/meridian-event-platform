package com.meridian.event.infrastructure.observability;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.util.Map;

public class KafkaCorrelationIdProducerInterceptor implements ProducerInterceptor<String, String> {

    public static final String CORRELATION_ID_HEADER = "correlationId";
    public static final String TRACE_ID_HEADER = "traceId";
    public static final String SPAN_ID_HEADER = "spanId";

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        String traceId = MDC.get(CorrelationIdFilter.MDC_TRACE_ID);
        String spanId = MDC.get(CorrelationIdFilter.MDC_SPAN_ID);

        if (correlationId != null) {
            record.headers().add(CORRELATION_ID_HEADER, correlationId.getBytes());
        }
        if (traceId != null) {
            record.headers().add(TRACE_ID_HEADER, traceId.getBytes());
        }
        if (spanId != null) {
            record.headers().add(SPAN_ID_HEADER, spanId.getBytes());
        }

        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}

    @Override
    public void close() {}

    @Override
    public void configure(Map<String, ?> configs) {}
}