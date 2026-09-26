package io.smallrye.reactive.messaging.kafka;

import java.util.Optional;
import org.eclipse.microprofile.config.Config;

/**
 * Extract the incoming configuration for the {@code smallrye-kafka} connector.
*/
public class KafkaConnectorIncomingConfiguration extends KafkaConnectorCommonConfiguration {

  /**
   * Creates a new KafkaConnectorIncomingConfiguration.
   */
  public KafkaConnectorIncomingConfiguration(Config config) {
    super(config);
    validate();
  }

  /**
  * Gets the topics value from the configuration.
  * Attribute Name: topics
  * Description: A comma-separating list of topics to be consumed. Cannot be used with the `topic` or `pattern` properties
  * @return the topics
  */
  public Optional<String> getTopics() {
    return config.getOptionalValue("topics", String.class);
  }

  /**
  * Gets the pattern value from the configuration.
  * Attribute Name: pattern
  * Description: Indicate that the `topic` property is a regular expression. Must be used with the `topic` property. Cannot be used with the `topics` property
  * Default Value: false
  * @return the pattern
  */
  public Boolean getPattern() {
    return config.getOptionalValue("pattern", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the assign-seek value from the configuration.
  * Attribute Name: assign-seek
  * Description: Assign partitions and optionally seek to offsets, instead of subscribing to topics. A comma-separating list of triplets in form of `<topic>:|<partition>|:<offset>` to assign statically to the consumer and seek to the given offsets. Offset `0` seeks to beginning and offset `-1` seeks to the end of the topic-partition. If the topic is omitted the configured topic will be used. If the offset is omitted partitions are assigned to the consumer but won't be seeked to offset.
  * @return the assign-seek
  */
  public Optional<String> getAssignSeek() {
    return config.getOptionalValue("assign-seek", String.class);
  }

  /**
  * Gets the key.deserializer value from the configuration.
  * Attribute Name: key.deserializer
  * Description: The deserializer classname used to deserialize the record's key
  * Default Value: org.apache.kafka.common.serialization.StringDeserializer
  * @return the key.deserializer
  */
  public String getKeyDeserializer() {
    return config.getOptionalValue("key.deserializer", String.class)
     .orElse("org.apache.kafka.common.serialization.StringDeserializer");
  }

  /**
  * Gets the value.deserializer value from the configuration.
  * Attribute Name: value.deserializer
  * Description: The deserializer classname used to deserialize the record's value
  * Mandatory: yes
  * @return the value.deserializer
  */
  public String getValueDeserializer() {
    return config.getOptionalValue("value.deserializer", String.class)
        .orElseThrow(() -> new IllegalArgumentException("The attribute `value.deserializer` on connector 'smallrye-kafka' (channel: " + getChannel() + ") must be set"));
  }

  /**
  * Gets the fetch.min.bytes value from the configuration.
  * Attribute Name: fetch.min.bytes
  * Description: The minimum amount of data the server should return for a fetch request. The default setting of 1 byte means that fetch requests are answered as soon as a single byte of data is available or the fetch request times out waiting for data to arrive.
  * Default Value: 1
  * @return the fetch.min.bytes
  */
  public Integer getFetchMinBytes() {
    return config.getOptionalValue("fetch.min.bytes", Integer.class)
     .orElse(Integer.valueOf("1"));
  }

  /**
  * Gets the group.id value from the configuration.
  * Attribute Name: group.id
  * Description: A unique string that identifies the consumer group the application belongs to. If not set, a unique, generated id is used
  * @return the group.id
  */
  public Optional<String> getGroupId() {
    return config.getOptionalValue("group.id", String.class);
  }

  /**
  * Gets the enable.auto.commit value from the configuration.
  * Attribute Name: enable.auto.commit
  * Description: If enabled, consumer's offset will be periodically committed in the background by the underlying Kafka client, ignoring the actual processing outcome of the records. It is recommended to NOT enable this setting and let Reactive Messaging handles the commit.
  * Default Value: false
  * @return the enable.auto.commit
  */
  public Boolean getEnableAutoCommit() {
    return config.getOptionalValue("enable.auto.commit", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the retry value from the configuration.
  * Attribute Name: retry
  * Description: Whether or not the connection to the broker is re-attempted in case of failure
  * Default Value: true
  * @return the retry
  */
  public Boolean getRetry() {
    return config.getOptionalValue("retry", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the retry-attempts value from the configuration.
  * Attribute Name: retry-attempts
  * Description: The maximum number of reconnection before failing. -1 means infinite retry
  * Default Value: -1
  * @return the retry-attempts
  */
  public Integer getRetryAttempts() {
    return config.getOptionalValue("retry-attempts", Integer.class)
     .orElse(Integer.valueOf("-1"));
  }

  /**
  * Gets the retry-max-wait value from the configuration.
  * Attribute Name: retry-max-wait
  * Description: The max delay (in seconds) between 2 reconnects
  * Default Value: 30
  * @return the retry-max-wait
  */
  public Integer getRetryMaxWait() {
    return config.getOptionalValue("retry-max-wait", Integer.class)
     .orElse(Integer.valueOf("30"));
  }

  /**
  * Gets the broadcast value from the configuration.
  * Attribute Name: broadcast
  * Description: Whether the Kafka records should be dispatched to multiple consumer
  * Default Value: false
  * @return the broadcast
  */
  public Boolean getBroadcast() {
    return config.getOptionalValue("broadcast", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the auto.offset.reset value from the configuration.
  * Attribute Name: auto.offset.reset
  * Description: What to do when there is no initial offset in Kafka.Accepted values are earliest, latest and none
  * Default Value: latest
  * @return the auto.offset.reset
  */
  public String getAutoOffsetReset() {
    return config.getOptionalValue("auto.offset.reset", String.class)
     .orElse("latest");
  }

  /**
  * Gets the failure-strategy value from the configuration.
  * Attribute Name: failure-strategy
  * Description: Specify the failure strategy to apply when a message produced from a record is acknowledged negatively (nack). Values can be `fail` (default), `ignore`, or `dead-letter-queue`
  * Default Value: fail
  * @return the failure-strategy
  */
  public String getFailureStrategy() {
    return config.getOptionalValue("failure-strategy", String.class)
     .orElse("fail");
  }

  /**
  * Gets the commit-strategy value from the configuration.
  * Attribute Name: commit-strategy
  * Description: Specify the commit strategy to apply when a message produced from a record is acknowledged. Values can be `latest`, `ignore` or `throttled`. If `enable.auto.commit` is true then the default is `ignore` otherwise it is `throttled`
  * @return the commit-strategy
  */
  public Optional<String> getCommitStrategy() {
    return config.getOptionalValue("commit-strategy", String.class);
  }

  /**
  * Gets the throttled.unprocessed-record-max-age.ms value from the configuration.
  * Attribute Name: throttled.unprocessed-record-max-age.ms
  * Description: While using the `throttled` commit-strategy, specify the max age in milliseconds that an unprocessed message can be before the connector is marked as unhealthy. Setting this attribute to 0 disables this monitoring.
  * Default Value: 60000
  * @return the throttled.unprocessed-record-max-age.ms
  */
  public Integer getThrottledUnprocessedRecordMaxAgeMs() {
    return config.getOptionalValue("throttled.unprocessed-record-max-age.ms", Integer.class)
     .orElse(Integer.valueOf("60000"));
  }

  /**
  * Gets the ordered value from the configuration.
  * Attribute Name: ordered
  * Description: Configures ordering guarantees for concurrent processing. Possible values: `key` for per-key sequential processing, or `partition` for per-partition sequential processing. Requires `@Blocking(ordered = false)` for concurrency. Works with any commit strategy.
  * @return the ordered
  */
  public Optional<String> getOrdered() {
    return config.getOptionalValue("ordered", String.class);
  }

  /**
  * Gets the ordered.max-concurrency value from the configuration.
  * Attribute Name: ordered.max-concurrency
  * Description: Maximum number of groups (keys or partitions) that can be processed concurrently. Defaults to `max.poll.records`.
  * @return the ordered.max-concurrency
  */
  public Optional<Integer> getOrderedMaxConcurrency() {
    return config.getOptionalValue("ordered.max-concurrency", Integer.class);
  }

  /**
  * Gets the throttled.ordered value from the configuration.
  * Attribute Name: throttled.ordered
  * Description: While using the `throttled` commit-strategy, configures ordering guarantees for concurrent processing. Possible values: `key` for per-key sequential processing within partitions, or `partition` for per-partition sequential processing. Requires `@Blocking(ordered = false)` for concurrency. Deprecated: Use `ordered` instead.
  * @return the throttled.ordered
@Deprecated
  */
  public Optional<String> getThrottledOrdered() {
    return config.getOptionalValue("throttled.ordered", String.class);
  }

  /**
  * Gets the throttled.ordered.max-concurrency value from the configuration.
  * Attribute Name: throttled.ordered.max-concurrency
  * Description: While using the `throttled` commit-strategy with ordered processing, specifies the maximum number of groups (keys or partitions) that can be processed concurrently. This should typically match your worker pool's `max-concurrency` setting. Defaults to `max.poll.records`. Deprecated: Use `ordered.max-concurrency` instead.
  * @return the throttled.ordered.max-concurrency
@Deprecated
  */
  public Optional<Integer> getThrottledOrderedMaxConcurrency() {
    return config.getOptionalValue("throttled.ordered.max-concurrency", Integer.class);
  }

  /**
  * Gets the checkpoint.state-store value from the configuration.
  * Attribute Name: checkpoint.state-store
  * Description: While using the `checkpoint` commit-strategy, the name set in `@Identifier` of a bean that implements `io.smallrye.reactive.messaging.kafka.StateStore.Factory` to specify the state store implementation.
  * @return the checkpoint.state-store
  */
  public Optional<String> getCheckpointStateStore() {
    return config.getOptionalValue("checkpoint.state-store", String.class);
  }

  /**
  * Gets the checkpoint.state-type value from the configuration.
  * Attribute Name: checkpoint.state-type
  * Description: While using the `checkpoint` commit-strategy, the fully qualified type name of the state object to persist in the state store. When provided, it can be used by the state store implementation to help persisting the processing state object.
  * @return the checkpoint.state-type
  */
  public Optional<String> getCheckpointStateType() {
    return config.getOptionalValue("checkpoint.state-type", String.class);
  }

  /**
  * Gets the checkpoint.unsynced-state-max-age.ms value from the configuration.
  * Attribute Name: checkpoint.unsynced-state-max-age.ms
  * Description: While using the `checkpoint` commit-strategy, specify the max age in milliseconds that the processing state must be persisted before the connector is marked as unhealthy. Setting this attribute to 0 disables this monitoring.
  * Default Value: 10000
  * @return the checkpoint.unsynced-state-max-age.ms
  */
  public Integer getCheckpointUnsyncedStateMaxAgeMs() {
    return config.getOptionalValue("checkpoint.unsynced-state-max-age.ms", Integer.class)
     .orElse(Integer.valueOf("10000"));
  }

  /**
  * Gets the dead-letter-queue.topic value from the configuration.
  * Attribute Name: dead-letter-queue.topic
  * Description: When the `failure-strategy` is set to `dead-letter-queue` indicates on which topic the record is sent. Defaults is `dead-letter-topic-$channel`
  * @return the dead-letter-queue.topic
  */
  public Optional<String> getDeadLetterQueueTopic() {
    return config.getOptionalValue("dead-letter-queue.topic", String.class);
  }

  /**
  * Gets the dead-letter-queue.producer-client-id value from the configuration.
  * Attribute Name: dead-letter-queue.producer-client-id
  * Description: When the `failure-strategy` is set to `dead-letter-queue` indicates what client id the generated producer should use. Defaults is `kafka-dead-letter-topic-producer-$client-id`
  * @return the dead-letter-queue.producer-client-id
  */
  public Optional<String> getDeadLetterQueueProducerClientId() {
    return config.getOptionalValue("dead-letter-queue.producer-client-id", String.class);
  }

  /**
  * Gets the dead-letter-queue.key.serializer value from the configuration.
  * Attribute Name: dead-letter-queue.key.serializer
  * Description: When the `failure-strategy` is set to `dead-letter-queue` indicates the key serializer to use. If not set the serializer associated to the key deserializer is used
  * @return the dead-letter-queue.key.serializer
  */
  public Optional<String> getDeadLetterQueueKeySerializer() {
    return config.getOptionalValue("dead-letter-queue.key.serializer", String.class);
  }

  /**
  * Gets the dead-letter-queue.value.serializer value from the configuration.
  * Attribute Name: dead-letter-queue.value.serializer
  * Description: When the `failure-strategy` is set to `dead-letter-queue` indicates the value serializer to use. If not set the serializer associated to the value deserializer is used
  * @return the dead-letter-queue.value.serializer
  */
  public Optional<String> getDeadLetterQueueValueSerializer() {
    return config.getOptionalValue("dead-letter-queue.value.serializer", String.class);
  }

  /**
  * Gets the delayed-retry-topic.topics value from the configuration.
  * Attribute Name: delayed-retry-topic.topics
  * Description: When the `failure-strategy` is set to `delayed-retry-topic` indicates topics to use. If not set the source channel name is used, with 10, 20 and 50 seconds delayed topics.
  * @return the delayed-retry-topic.topics
  */
  public Optional<String> getDelayedRetryTopicTopics() {
    return config.getOptionalValue("delayed-retry-topic.topics", String.class);
  }

  /**
  * Gets the delayed-retry-topic.max-retries value from the configuration.
  * Attribute Name: delayed-retry-topic.max-retries
  * Description: When the `failure-strategy` is set to `delayed-retry-topic` indicates the maximum number of retries. If higher than the number of delayed retry topics, last topic is used.
  * @return the delayed-retry-topic.max-retries
  */
  public Optional<Integer> getDelayedRetryTopicMaxRetries() {
    return config.getOptionalValue("delayed-retry-topic.max-retries", Integer.class);
  }

  /**
  * Gets the delayed-retry-topic.timeout value from the configuration.
  * Attribute Name: delayed-retry-topic.timeout
  * Description: When the `failure-strategy` is set to `delayed-retry-topic` indicates the global timeout per record.
  * Default Value: 120000
  * @return the delayed-retry-topic.timeout
  */
  public Integer getDelayedRetryTopicTimeout() {
    return config.getOptionalValue("delayed-retry-topic.timeout", Integer.class)
     .orElse(Integer.valueOf("120000"));
  }

  /**
  * Gets the partitions value from the configuration.
  * Attribute Name: partitions
  * Description: The number of partitions to be consumed concurrently. The connector creates the specified amount of Kafka consumers. It should match the number of partition of the targeted topic. Deprecated: Use `concurrency` channel attribute instead.
  * Default Value: 1
  * @return the partitions
@Deprecated
  */
  public Integer getPartitions() {
    return config.getOptionalValue("partitions", Integer.class)
     .orElse(Integer.valueOf("1"));
  }

  /**
  * Gets the requests value from the configuration.
  * Attribute Name: requests
  * Description: When `partitions` is greater than 1, this attribute allows configuring how many records are requested by each consumers every time. Deprecated: Use `concurrency` channel attribute instead.
  * Default Value: 128
  * @return the requests
@Deprecated
  */
  public Integer getRequests() {
    return config.getOptionalValue("requests", Integer.class)
     .orElse(Integer.valueOf("128"));
  }

  /**
  * Gets the consumer-rebalance-listener.name value from the configuration.
  * Attribute Name: consumer-rebalance-listener.name
  * Description: The name set in `@Identifier` of a bean that implements `io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener`. If set, this rebalance listener is applied to the consumer.
  * @return the consumer-rebalance-listener.name
  */
  public Optional<String> getConsumerRebalanceListenerName() {
    return config.getOptionalValue("consumer-rebalance-listener.name", String.class);
  }

  /**
  * Gets the key-deserialization-failure-handler value from the configuration.
  * Attribute Name: key-deserialization-failure-handler
  * Description: The name set in `@Identifier` of a bean that implements `io.smallrye.reactive.messaging.kafka.DeserializationFailureHandler`. If set, deserialization failure happening when deserializing keys are delegated to this handler which may retry or provide a fallback value.
  * @return the key-deserialization-failure-handler
  */
  public Optional<String> getKeyDeserializationFailureHandler() {
    return config.getOptionalValue("key-deserialization-failure-handler", String.class);
  }

  /**
  * Gets the value-deserialization-failure-handler value from the configuration.
  * Attribute Name: value-deserialization-failure-handler
  * Description: The name set in `@Identifier` of a bean that implements `io.smallrye.reactive.messaging.kafka.DeserializationFailureHandler`. If set, deserialization failure happening when deserializing values are delegated to this handler which may retry or provide a fallback value.
  * @return the value-deserialization-failure-handler
  */
  public Optional<String> getValueDeserializationFailureHandler() {
    return config.getOptionalValue("value-deserialization-failure-handler", String.class);
  }

  /**
  * Gets the fail-on-deserialization-failure value from the configuration.
  * Attribute Name: fail-on-deserialization-failure
  * Description: When no deserialization failure handler is set and a deserialization failure happens, report the failure and mark the application as unhealthy. If set to `false` and a deserialization failure happens, a `null` value is forwarded.
  * Default Value: true
  * @return the fail-on-deserialization-failure
  */
  public Boolean getFailOnDeserializationFailure() {
    return config.getOptionalValue("fail-on-deserialization-failure", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the graceful-shutdown value from the configuration.
  * Attribute Name: graceful-shutdown
  * Description: Whether or not a graceful shutdown should be attempted when the application terminates.
  * Default Value: true
  * @return the graceful-shutdown
  */
  public Boolean getGracefulShutdown() {
    return config.getOptionalValue("graceful-shutdown", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the poll-timeout value from the configuration.
  * Attribute Name: poll-timeout
  * Description: The polling timeout in milliseconds. When polling records, the poll will wait at most that duration before returning records. Default is 1000ms
  * Default Value: 1000
  * @return the poll-timeout
  */
  public Integer getPollTimeout() {
    return config.getOptionalValue("poll-timeout", Integer.class)
     .orElse(Integer.valueOf("1000"));
  }

  /**
  * Gets the pause-if-no-requests value from the configuration.
  * Attribute Name: pause-if-no-requests
  * Description: Whether the polling must be paused when the application does not request items and resume when it does. This allows implementing back-pressure based on the application capacity. Note that polling is not stopped, but will not retrieve any records when paused.
  * Default Value: true
  * @return the pause-if-no-requests
  */
  public Boolean getPauseIfNoRequests() {
    return config.getOptionalValue("pause-if-no-requests", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the batch value from the configuration.
  * Attribute Name: batch
  * Description: Whether the Kafka records are consumed in batch. The channel injection point must consume a compatible type, such as `List<Payload>` or `KafkaRecordBatch<Payload>`.
  * Default Value: false
  * @return the batch
  */
  public Boolean getBatch() {
    return config.getOptionalValue("batch", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the max-queue-size-factor value from the configuration.
  * Attribute Name: max-queue-size-factor
  * Description: Multiplier factor to determine maximum number of records queued for processing, using `max.poll.records` * `max-queue-size-factor`. Defaults to 2. In `batch` mode `max.poll.records` is considered `1`.
  * Default Value: 2
  * @return the max-queue-size-factor
  */
  public Integer getMaxQueueSizeFactor() {
    return config.getOptionalValue("max-queue-size-factor", Integer.class)
     .orElse(Integer.valueOf("2"));
  }

  /**
  * Gets the share-group value from the configuration.
  * Attribute Name: share-group
  * Description: Deprecated, use 'share-group.enabled' instead
  * Default Value: false
  * @return the share-group
@Deprecated
  */
  public Boolean getShareGroup() {
    return config.getOptionalValue("share-group", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the share-group.enabled value from the configuration.
  * Attribute Name: share-group.enabled
  * Description: Whether to use Kafka Share Groups for consumption. When enabled, the consumer will use a ShareConsumer which provides cooperative record processing across multiple consumers without explicit partition assignment.
  * Default Value: false
  * @return the share-group.enabled
  */
  public Boolean getShareGroupEnabled() {
    return config.getOptionalValue("share-group.enabled", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the share-group.unprocessed-record-max-age.ms value from the configuration.
  * Attribute Name: share-group.unprocessed-record-max-age.ms
  * Description: While using share groups, specify the max age in milliseconds that an unprocessed record can be before the connector reports a failure. Setting this attribute to 0 disables this monitoring.
  * Default Value: 60000
  * @return the share-group.unprocessed-record-max-age.ms
  */
  public Integer getShareGroupUnprocessedRecordMaxAgeMs() {
    return config.getOptionalValue("share-group.unprocessed-record-max-age.ms", Integer.class)
     .orElse(Integer.valueOf("60000"));
  }

  /**
  * Gets the share-group.failure-acknowledgement-type value from the configuration.
  * Attribute Name: share-group.failure-acknowledgement-type
  * Description: Default acknowledgement type to apply to the record, when the message is nacked.
  * Default Value: release
  * @return the share-group.failure-acknowledgement-type
  */
  public String getShareGroupFailureAcknowledgementType() {
    return config.getOptionalValue("share-group.failure-acknowledgement-type", String.class)
     .orElse("release");
  }

  /**
  * Gets the share-group.failure-deserialization-acknowledgement-type value from the configuration.
  * Attribute Name: share-group.failure-deserialization-acknowledgement-type
  * Description: Default acknowledgement type to apply to the record, when the message is nacked because of failure on deserialization.
  * Default Value: reject
  * @return the share-group.failure-deserialization-acknowledgement-type
  */
  public String getShareGroupFailureDeserializationAcknowledgementType() {
    return config.getOptionalValue("share-group.failure-deserialization-acknowledgement-type", String.class)
     .orElse("reject");
  }

  public void validate() {
    super.validate();
    getValueDeserializer();
  }
}
