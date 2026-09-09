package com.meridian.event.infrastructure.resilience;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KafkaTestHelper {

    public static ConsumerRecords<String, String> getRecords(
            ConsumerFactory<String, String> consumerFactory,
            String topic,
            long timeoutMs,
            int maxRecords) {

        String groupId = "test-group-" + UUID.randomUUID();
        Map<String, Object> configs = new HashMap<>(consumerFactory.getConfigurationProperties());
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        Consumer<String, String> consumer = consumerFactory.createConsumer(groupId);
        consumer.subscribe(List.of(topic));
        return KafkaTestUtils.getRecords(consumer, Duration.ofMillis(timeoutMs), maxRecords);
    }
}
