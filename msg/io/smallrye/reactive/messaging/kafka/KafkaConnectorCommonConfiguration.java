package io.smallrye.reactive.messaging.kafka;

import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

/**
 * Extracts the common configuration for the {@code smallrye-kafka} connector.
*/
 public class KafkaConnectorCommonConfiguration {
  protected final Config config;

  /**
   * Creates a new KafkaConnectorCommonConfiguration.
   */
  public KafkaConnectorCommonConfiguration(Config config) {
    this.config = config;
  }

  /**
   * @return the connector configuration
   */
  public Config config() {
    return this.config;
  }

  /**
   * Retrieves the value stored for the given alias.
   * @param alias the attribute alias, must not be {@code null} or blank
   * @param type the targeted type
   * @param <T> the targeted type
   * @return the configuration value for the given alias, empty if not set
   */
  protected <T> Optional<T> getFromAlias(String alias, Class<T> type) {
    return ConfigProvider.getConfig().getOptionalValue(alias, type);
  }

  /**
   * Retrieves the value stored for the given alias. Returns the default value if not present.
   * @param alias the attribute alias, must not be {@code null} or blank
   * @param type the targeted type
   * @param defaultValue the default value
   * @param <T> the targeted type
   * @return the configuration value for the given alias, empty if not set
   */
  protected <T> T getFromAliasWithDefaultValue(String alias, Class<T> type, T defaultValue) {
    return getFromAlias(alias, type).orElse(defaultValue);
  }

  /**
   * @return the channel name
   */
  public String getChannel() {
    return config.getValue(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, String.class);
  }

  /**
  * Gets the bootstrap.servers value from the configuration.
  * Attribute Name: bootstrap.servers
  * Description: A comma-separated list of host:port to use for establishing the initial connection to the Kafka cluster.
  * MicroProfile Config Alias: kafka.bootstrap.servers
  * Default Value: localhost:9092
  * @return the bootstrap.servers
  */
  public String getBootstrapServers() {
    return config.getOptionalValue("bootstrap.servers", String.class)
     .orElseGet(() -> getFromAliasWithDefaultValue("kafka.bootstrap.servers", String.class, "localhost:9092"));
  }

  /**
  * Gets the topic value from the configuration.
  * Attribute Name: topic
  * Description: The consumed / populated Kafka topic. If neither this property nor the `topics` properties are set, the channel name is used
  * @return the topic
  */
  public Optional<String> getTopic() {
    return config.getOptionalValue("topic", String.class);
  }

  /**
  * Gets the health-enabled value from the configuration.
  * Attribute Name: health-enabled
  * Description: Whether health reporting is enabled (default) or disabled
  * Default Value: true
  * @return the health-enabled
  */
  public Boolean getHealthEnabled() {
    return config.getOptionalValue("health-enabled", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the health-readiness-enabled value from the configuration.
  * Attribute Name: health-readiness-enabled
  * Description: Whether readiness health reporting is enabled (default) or disabled
  * Default Value: true
  * @return the health-readiness-enabled
  */
  public Boolean getHealthReadinessEnabled() {
    return config.getOptionalValue("health-readiness-enabled", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the health-readiness-topic-verification value from the configuration.
  * Attribute Name: health-readiness-topic-verification
  * Description: Whether the readiness check should verify that topics exist on the broker. Default to false. Enabling it requires an admin connection. Deprecated: Use 'health-topic-verification-enabled' instead.
  * @return the health-readiness-topic-verification
@Deprecated
  */
  public Optional<Boolean> getHealthReadinessTopicVerification() {
    return config.getOptionalValue("health-readiness-topic-verification", Boolean.class);
  }

  /**
  * Gets the health-readiness-timeout value from the configuration.
  * Attribute Name: health-readiness-timeout
  * Description: During the readiness health check, the connector connects to the broker and retrieves the list of topics. This attribute specifies the maximum duration (in ms) for the retrieval. If exceeded, the channel is considered not-ready. Deprecated: Use 'health-topic-verification-timeout' instead.
  * @return the health-readiness-timeout
@Deprecated
  */
  public Optional<Long> getHealthReadinessTimeout() {
    return config.getOptionalValue("health-readiness-timeout", Long.class);
  }

  /**
  * Gets the health-topic-verification-enabled value from the configuration.
  * Attribute Name: health-topic-verification-enabled
  * Description: Whether the startup and readiness check should verify that topics exist on the broker. Default to false. Enabling it requires an admin client connection.
  * Default Value: false
  * @return the health-topic-verification-enabled
  */
  public Boolean getHealthTopicVerificationEnabled() {
    return config.getOptionalValue("health-topic-verification-enabled", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the health-topic-verification-timeout value from the configuration.
  * Attribute Name: health-topic-verification-timeout
  * Description: During the startup and readiness health check, the connector connects to the broker and retrieves the list of topics. This attribute specifies the maximum duration (in ms) for the retrieval. If exceeded, the channel is considered not-ready.
  * Default Value: 2000
  * @return the health-topic-verification-timeout
  */
  public Long getHealthTopicVerificationTimeout() {
    return config.getOptionalValue("health-topic-verification-timeout", Long.class)
     .orElse(Long.valueOf("2000"));
  }

  /**
  * Gets the health-topic-verification-startup-disabled value from the configuration.
  * Attribute Name: health-topic-verification-startup-disabled
  * Description: Whether the topic verification is disabled for the startup health check.
  * Default Value: false
  * @return the health-topic-verification-startup-disabled
  */
  public Boolean getHealthTopicVerificationStartupDisabled() {
    return config.getOptionalValue("health-topic-verification-startup-disabled", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the health-topic-verification-readiness-disabled value from the configuration.
  * Attribute Name: health-topic-verification-readiness-disabled
  * Description: Whether the topic verification is disabled for the readiness health check.
  * Default Value: false
  * @return the health-topic-verification-readiness-disabled
  */
  public Boolean getHealthTopicVerificationReadinessDisabled() {
    return config.getOptionalValue("health-topic-verification-readiness-disabled", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  /**
  * Gets the tracing-enabled value from the configuration.
  * Attribute Name: tracing-enabled
  * Description: Whether tracing is enabled (default) or disabled
  * Default Value: true
  * @return the tracing-enabled
  */
  public Boolean getTracingEnabled() {
    return config.getOptionalValue("tracing-enabled", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the cloud-events value from the configuration.
  * Attribute Name: cloud-events
  * Description: Enables (default) or disables the Cloud Event support. If enabled on an _incoming_ channel, the connector analyzes the incoming records and try to create Cloud Event metadata. If enabled on an _outgoing_, the connector sends the outgoing messages as Cloud Event if the message includes Cloud Event Metadata.
  * Default Value: true
  * @return the cloud-events
  */
  public Boolean getCloudEvents() {
    return config.getOptionalValue("cloud-events", Boolean.class)
     .orElse(Boolean.valueOf("true"));
  }

  /**
  * Gets the kafka-configuration value from the configuration.
  * Attribute Name: kafka-configuration
  * Description: Identifier of a CDI bean that provides the default Kafka consumer/producer configuration for this channel. The channel configuration can still override any attribute. The bean must have a type of Map<String, Object> and must use the @io.smallrye.common.annotation.Identifier qualifier to set the identifier.
  * @return the kafka-configuration
  */
  public Optional<String> getKafkaConfiguration() {
    return config.getOptionalValue("kafka-configuration", String.class);
  }

  /**
  * Gets the client-id-prefix value from the configuration.
  * Attribute Name: client-id-prefix
  * Description: Prefix for Kafka client `client.id` attribute. If defined configured or generated `client.id` will be prefixed with the given value.
  * @return the client-id-prefix
  */
  public Optional<String> getClientIdPrefix() {
    return config.getOptionalValue("client-id-prefix", String.class);
  }

  /**
  * Gets the lazy-client value from the configuration.
  * Attribute Name: lazy-client
  * Description: Whether Kafka client is created lazily or eagerly.
  * Default Value: false
  * @return the lazy-client
  */
  public Boolean getLazyClient() {
    return config.getOptionalValue("lazy-client", Boolean.class)
     .orElse(Boolean.valueOf("false"));
  }

  public void validate() {
  }
}
