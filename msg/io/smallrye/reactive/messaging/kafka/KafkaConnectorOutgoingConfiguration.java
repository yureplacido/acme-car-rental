package io.smallrye.reactive.messaging.kafka;

import java.util.Optional;
import org.eclipse.microprofile.config.Config;

/**
 * Extract the outgoing configuration for the {@code smallrye-kafka} connector.
*/
public class KafkaConnectorOutgoingConfiguration extends KafkaConnectorCommonConfiguration {

  /**
   * Creates a new KafkaConnectorOutgoingConfiguration.
   */
  public KafkaConnectorOutgoingConfiguration(Config config) {
    super(config);
    validate();
  }

  /**
  * Gets the key.serializer value from the configuration.
  * Attribute Name: key.serializer
  * Description: The serializer classname used to serialize the record's key
  * Default Value: org.apache.kafka.common.serialization.StringSerializer
  * @return the key.serializer
  */
  public String getKeySerializer() {
    return config.getOptionalValue("key.serializer", String.class)
     .orElse("org.apache.kafka.common.serialization.StringSerializer");
  }

  /**
  * Gets the value.serializer value from the configuration.
  * Attribute Name: value.serializer
  * Description: The serializer classname used to serialize the payload
  * Mandatory: yes
  * @return the value.serializer
  */
  public String getValueSerializer() {
    return config.getOptionalValue("value.serializer", String.class)
        .orElseThrow(() -> new IllegalArgumentException("The attribute `value.serializer` on connector 'smallrye-kafka' (channel: " + getChannel() + ") must be set"));
  }

  /**
  * Gets the acks value from the configuration.
  * Attribute Name: acks
  * Description: The number of acknowledgments the producer requires the leader to have received before considering a request complete. This controls the durability of records that are sent. Accepted values are: 0, 1, all
  * Default Value: 1
  * @return the acks
  */
  public String getAcks() {
    return config.getOptionalValue("acks", String.class)
     .orElse("1");
  }

  /**
  * Gets the buffer.memory value from the configuration.
  * Attribute Name: buffer.memory
  * Description: The total bytes of memory the producer can use to buffer records waiting to be sent to the server.
  * Default Value: 33554432
  * @return the buffer.memory
  */
  public Long getBufferMemory() {
    return config.getOptionalValue("buffer.memory", Long.class)
     .orElse(Long.valueOf("33554432"));
  }

  /**
  * Gets the retries value from the configuration.
  * Attribute Name: retries
  * Description: If set to a positive number, the connector will try to resend any record that was not delivered successfully (with a potentially transient error) until the number of retries is reached. If set to 0, retries are disabled. If not set, the connector tries to resend any record that failed to be delivered (because of a potentially transient error) during an amount of time configured by `delivery.timeout.ms`.
  * Default Value: 2147483647
  * @return the retries
  */
  public Long getRetries() {
    return config.getOptionalValue("retries", Long.class)
     .orElse(Long.valueOf("2147483647"));
  }

  /**
  * Gets the key value from the configuration.
  * Attribute Name: key
  * Description: A key to used when writing the record
  * @return the key
  */
  public Optional<String> getKey() {
    return config.getOptionalValue("key", String.class);
  }

  /**
  * Gets the partition value from the configuration.
  * Attribute Name: partition
  * Description: The target partition id. -1 to let the client determine the partition
  * Default Value: -1
  * @return the partition
  */
  public Integer getPartition() {
    return config.getOptionalValue("partition", Integer.class)
     .orElse(Integer.valueOf("-1"));
  }

  /**
  * Gets the waitForWriteCompletion value from the configuration.
  * Attribute Name: waitForWriteCompletion
  * Description: Whether the client waits for Kafka to acknowledge the written record before acknowledging the message
  * Default Value: true
  * @return the waitForWriteCompletion
  */
  public Boolean getWaitForWriteCompletion() {
    return config.getOptionalValue("waitForWriteCompletion", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the max-inflight-messages value from the configuration.
  * Attribute Name: max-inflight-messages
  * Description: The maximum number of messages to be written to Kafka concurrently. It limits the number of messages waiting to be written and acknowledged by the broker. You can set this attribute to `0` remove the limit
  * Default Value: 1024
  * @return the max-inflight-messages
  */
  public Long getMaxInflightMessages() {
    return config.getOptionalValue("max-inflight-messages", Long.class)
     .orElse(Long.valueOf("1024"));
  }

  /**
  * Gets the cloud-events-source value from the configuration.
  * Attribute Name: cloud-events-source
  * Description: Configure the default `source` attribute of the outgoing Cloud Event. Requires `cloud-events` to be set to `true`. This value is used if the message does not configure the `source` attribute itself
  * MicroProfile Config Alias: cloud-events-default-source
  * @return the cloud-events-source
  */
  public Optional<String> getCloudEventsSource() {
    Optional<String> maybe = config.getOptionalValue("cloud-events-source", String.class);
    if (maybe.isPresent()) { return maybe; }
    return getFromAlias("cloud-events-default-source", String.class);
  }

  /**
  * Gets the cloud-events-type value from the configuration.
  * Attribute Name: cloud-events-type
  * Description: Configure the default `type` attribute of the outgoing Cloud Event. Requires `cloud-events` to be set to `true`. This value is used if the message does not configure the `type` attribute itself
  * MicroProfile Config Alias: cloud-events-default-type
  * @return the cloud-events-type
  */
  public Optional<String> getCloudEventsType() {
    Optional<String> maybe = config.getOptionalValue("cloud-events-type", String.class);
    if (maybe.isPresent()) { return maybe; }
    return getFromAlias("cloud-events-default-type", String.class);
  }

  /**
  * Gets the cloud-events-subject value from the configuration.
  * Attribute Name: cloud-events-subject
  * Description: Configure the default `subject` attribute of the outgoing Cloud Event. Requires `cloud-events` to be set to `true`. This value is used if the message does not configure the `subject` attribute itself
  * MicroProfile Config Alias: cloud-events-default-subject
  * @return the cloud-events-subject
  */
  public Optional<String> getCloudEventsSubject() {
    Optional<String> maybe = config.getOptionalValue("cloud-events-subject", String.class);
    if (maybe.isPresent()) { return maybe; }
    return getFromAlias("cloud-events-default-subject", String.class);
  }

  /**
  * Gets the cloud-events-data-content-type value from the configuration.
  * Attribute Name: cloud-events-data-content-type
  * Description: Configure the default `datacontenttype` attribute of the outgoing Cloud Event. Requires `cloud-events` to be set to `true`. This value is used if the message does not configure the `datacontenttype` attribute itself
  * MicroProfile Config Alias: cloud-events-default-data-content-type
  * @return the cloud-events-data-content-type
  */
  public Optional<String> getCloudEventsDataContentType() {
    Optional<String> maybe = config.getOptionalValue("cloud-events-data-content-type", String.class);
    if (maybe.isPresent()) { return maybe; }
    return getFromAlias("cloud-events-default-data-content-type", String.class);
  }

  /**
  * Gets the cloud-events-data-schema value from the configuration.
  * Attribute Name: cloud-events-data-schema
  * Description: Configure the default `dataschema` attribute of the outgoing Cloud Event. Requires `cloud-events` to be set to `true`. This value is used if the message does not configure the `dataschema` attribute itself
  * MicroProfile Config Alias: cloud-events-default-data-schema
  * @return the cloud-events-data-schema
  */
  public Optional<String> getCloudEventsDataSchema() {
    Optional<String> maybe = config.getOptionalValue("cloud-events-data-schema", String.class);
    if (maybe.isPresent()) { return maybe; }
    return getFromAlias("cloud-events-default-data-schema", String.class);
  }

  /**
  * Gets the cloud-events-insert-timestamp value from the configuration.
  * Attribute Name: cloud-events-insert-timestamp
  * Description: Whether or not the connector should insert automatically the `time` attribute into the outgoing Cloud Event. Requires `cloud-events` to be set to `true`. This value is used if the message does not configure the `time` attribute itself
  * MicroProfile Config Alias: cloud-events-default-timestamp
  * Default Value: true
  * @return the cloud-events-insert-timestamp
  */
  public Boolean getCloudEventsInsertTimestamp() {
    return config.getOptionalValue("cloud-events-insert-timestamp", Boolean.class)
     .orElseGet(() -> getFromAliasWithDefaultValue("cloud-events-default-timestamp", Boolean.class, Boolean.valueOf("true")));
  }

  /**
  * Gets the cloud-events-mode value from the configuration.
  * Attribute Name: cloud-events-mode
  * Description: The Cloud Event mode (`structured` or `binary` (default)). Indicates how are written the cloud events in the outgoing record
  * Default Value: binary
  * @return the cloud-events-mode
  */
  public String getCloudEventsMode() {
    return config.getOptionalValue("cloud-events-mode", String.class)
     .orElse("binary");
  }

  /**
  * Gets the close-timeout value from the configuration.
  * Attribute Name: close-timeout
  * Description: The amount of milliseconds waiting for a graceful shutdown of the Kafka producer
  * Default Value: 10000
  * @return the close-timeout
  */
  public Integer getCloseTimeout() {
    return config.getOptionalValue("close-timeout", Integer.class)
     .orElse(Integer.valueOf("10000"));
  }

  /**
  * Gets the merge value from the configuration.
  * Attribute Name: merge
  * Description: Whether the connector should allow multiple upstreams
  * Default Value: false
  * @return the merge
  */
  public Boolean getMerge() {
    return config.getOptionalValue("merge", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the propagate-record-key value from the configuration.
  * Attribute Name: propagate-record-key
  * Description: Propagate incoming record key to the outgoing record
  * Default Value: false
  * @return the propagate-record-key
  */
  public Boolean getPropagateRecordKey() {
    return config.getOptionalValue("propagate-record-key", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the propagate-headers value from the configuration.
  * Attribute Name: propagate-headers
  * Description: A comma-separating list of incoming record headers to be propagated to the outgoing record
  * Default Value: 
  * @return the propagate-headers
  */
  public String getPropagateHeaders() {
    return config.getOptionalValue("propagate-headers", String.class)
     .orElse("");
  }

  /**
  * Gets the key-serialization-failure-handler value from the configuration.
  * Attribute Name: key-serialization-failure-handler
  * Description: The name set in `@Identifier` of a bean that implements `io.smallrye.reactive.messaging.kafka.SerializationFailureHandler`. If set, serialization failure happening when serializing keys are delegated to this handler which may provide a fallback value.
  * @return the key-serialization-failure-handler
  */
  public Optional<String> getKeySerializationFailureHandler() {
    return config.getOptionalValue("key-serialization-failure-handler", String.class);
  }

  /**
  * Gets the value-serialization-failure-handler value from the configuration.
  * Attribute Name: value-serialization-failure-handler
  * Description: The name set in `@Identifier` of a bean that implements `io.smallrye.reactive.messaging.kafka.SerializationFailureHandler`. If set, serialization failure happening when serializing values are delegated to this handler which may provide a fallback value.
  * @return the value-serialization-failure-handler
  */
  public Optional<String> getValueSerializationFailureHandler() {
    return config.getOptionalValue("value-serialization-failure-handler", String.class);
  }

  /**
  * Gets the interceptor-bean value from the configuration.
  * Attribute Name: interceptor-bean
  * Description: The name set in `@Identifier` of a bean that implements `org.apache.kafka.clients.producer.ProducerInterceptor`. If set, the identified bean will be used as the producer interceptor.
  * @return the interceptor-bean
  */
  public Optional<String> getInterceptorBean() {
    return config.getOptionalValue("interceptor-bean", String.class);
  }

  /**
  * Gets the pooled-producer value from the configuration.
  * Attribute Name: pooled-producer
  * Description: Deprecated, use 'pooled-producer.enabled' instead
  * Default Value: false
  * @return the pooled-producer
@Deprecated
  */
  public Boolean getPooledProducer() {
    return config.getOptionalValue("pooled-producer", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the pooled-producer.enabled value from the configuration.
  * Attribute Name: pooled-producer.enabled
  * Description: Whether to use a pool of Kafka producers for concurrent transactions. Each transaction scope acquires a producer from the pool, enabling concurrent exactly-once processing. Requires transactional.id to be set.
  * Default Value: false
  * @return the pooled-producer.enabled
  */
  public Boolean getPooledProducerEnabled() {
    return config.getOptionalValue("pooled-producer.enabled", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the pooled-producer.initial-pool-size value from the configuration.
  * Attribute Name: pooled-producer.initial-pool-size
  * Description: Number of Kafka producers to pre-create in the pool at startup. Respects the lazy-client setting. Defaults to 0 (lazy creation).
  * Default Value: 0
  * @return the pooled-producer.initial-pool-size
  */
  public Integer getPooledProducerInitialPoolSize() {
    return config.getOptionalValue("pooled-producer.initial-pool-size", Integer.class)
     .orElse(Integer.valueOf("0"));
  }

  /**
  * Gets the pooled-producer.max-pool-size value from the configuration.
  * Attribute Name: pooled-producer.max-pool-size
  * Description: Maximum number of Kafka producers in the pool. When all producers are in use and the pool is exhausted, an exception is thrown. Defaults to 10.
  * Default Value: 10
  * @return the pooled-producer.max-pool-size
  */
  public Integer getPooledProducerMaxPoolSize() {
    return config.getOptionalValue("pooled-producer.max-pool-size", Integer.class)
     .orElse(Integer.valueOf("10"));
  }

  public void validate() {
    super.validate();
    getValueSerializer();
  }
}
