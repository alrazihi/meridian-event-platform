package com.meridian.event.infrastructure.observability;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.MDC;

import java.util.Map;

public class KafkaCorrelationIdConsumerInterceptor implements ConsumerInterceptor<String, String> {

    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> records) {
        // We'll set MDC per-record in the listener, not here
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

    @Override
    public void close() {}

    @Override
    public void configure(Map<String, ?> configs) {}

    public static void setMdcFromRecord(ConsumerRecord<String, String> record) {
        record.headers().forEach(header -> {
            switch (header.key()) {
                case KafkaCorrelationIdProducerInterceptor.CORRELATION_ID_HEADER ->
                        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID, new String(header.value()));
                case KafkaCorrelationIdProducerInterceptor.TRACE_ID_HEADER ->
                        MDC.put(CorrelationIdFilter.MDC_TRACE_ID, new String(header.value()));
                case KafkaCorrelationIdProducerInterceptor.SPAN_ID_HEADER ->
                        MDC.put(CorrelationIdFilter.MDC_SPAN_ID, new String(header.value()));
            }
        });
    }

    public static void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_CORRELATION_ID);
        MDC.remove(CorrelationIdFilter.MDC_TRACE_ID);
        MDC.remove(CorrelationIdFilter.MDC_SPAN_ID);
    }
}