package io.smallrye.reactive.messaging.kafka.i18n;

import java.util.Locale;
import java.io.Serializable;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.DelegatingBasicLogger;
import java.lang.String;
import org.jboss.logging.Logger;
import org.apache.kafka.common.TopicPartition;
import org.jboss.logging.BasicLogger;
import java.lang.Throwable;
import java.util.Collection;
import java.lang.Object;
import java.util.Map;


import static org.jboss.logging.Logger.Level.TRACE;
import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.FATAL;
import static org.jboss.logging.Logger.Level.WARN;

/**
 * Warning this class consists of generated code.
 */
public class KafkaLogging_$logger extends DelegatingBasicLogger implements KafkaLogging, BasicLogger, Serializable {
    private static final long serialVersionUID = 1L;
    private static final String FQCN = KafkaLogging_$logger.class.getName();
    public KafkaLogging_$logger(final Logger log) {
        super(log);
    }
    private static final Locale LOCALE = Locale.ROOT;
    protected Locale getLoggingLocale() {
        return LOCALE;
    }
    @Override
    public final void deadLetterConfig(final String deadQueueTopic, final String keySerializer, final String valueSerializer) {
        super.log.logf(FQCN, DEBUG, null, deadLetterConfig$str(), deadQueueTopic, keySerializer, valueSerializer);
    }
    protected String deadLetterConfig$str() {
        return "SRMSG18201: Dead queue letter configured with: topic: `%s`, key serializer: `%s`, value serializer: `%s`";
    }
    @Override
    public final void messageNackedDeadLetter(final String channel, final String topic) {
        super.log.logf(FQCN, INFO, null, messageNackedDeadLetter$str(), channel, topic);
    }
    protected String messageNackedDeadLetter$str() {
        return "SRMSG18202: A message sent to channel `%s` has been nacked, sending the record to a dead letter topic %s";
    }
    @Override
    public final void messageNackedFailStop(final String channel) {
        super.log.logf(FQCN, ERROR, null, messageNackedFailStop$str(), channel);
    }
    protected String messageNackedFailStop$str() {
        return "SRMSG18203: A message sent to channel `%s` has been nacked, fail-stop";
    }
    @Override
    public final void messageNackedIgnore(final String channel, final String reason) {
        super.log.logf(FQCN, WARN, null, messageNackedIgnore$str(), channel, reason);
    }
    protected String messageNackedIgnore$str() {
        return "SRMSG18204: A message sent to channel `%s` has been nacked, ignored failure is: %s.";
    }
    @Override
    public final void messageNackedFullIgnored(final Throwable t) {
        super.log.logf(FQCN, DEBUG, t, messageNackedFullIgnored$str());
    }
    protected String messageNackedFullIgnored$str() {
        return "SRMSG18205: The full ignored failure is";
    }
    @Override
    public final void unableToWrite(final String channel, final String topic, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToWrite2$str(), channel, topic);
    }
    protected String unableToWrite2$str() {
        return "SRMSG18206: Unable to write to Kafka from channel %s (topic: %s)";
    }
    @Override
    public final void unableToWrite(final String channel, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToWrite1$str(), channel);
    }
    protected String unableToWrite1$str() {
        return "SRMSG18206: Unable to write to Kafka from channel %s (no topic set)";
    }
    @Override
    public final void unableToDispatch(final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToDispatch$str());
    }
    protected String unableToDispatch$str() {
        return "SRMSG18207: Unable to dispatch message to Kafka";
    }
    @Override
    public final void sendingMessageToTopic(final Message<?> message, final String channel, final String topic) {
        super.log.logf(FQCN, DEBUG, null, sendingMessageToTopic$str(), message, channel, topic);
    }
    protected String sendingMessageToTopic$str() {
        return "SRMSG18209: Sending message %s from channel '%s' to Kafka topic '%s'";
    }
    @Override
    public final void unableToSendRecord(final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToSendRecord$str());
    }
    protected String unableToSendRecord$str() {
        return "SRMSG18210: Unable to send a record to Kafka ";
    }
    @Override
    public final void successfullyToTopic(final Message<?> message, final String channel, final String topic, final int partition, final long offset) {
        super.log.logf(FQCN, DEBUG, null, successfullyToTopic$str(), message, channel, topic, partition, offset);
    }
    protected String successfullyToTopic$str() {
        return "SRMSG18211: Message %s from channel '%s' sent successfully to Kafka topic-partition '%s-%d', with offset %d";
    }
    @Override
    public final void nackingMessage(final Message<?> message, final String channel, final String topic, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, nackingMessage$str(), message, channel, topic);
    }
    protected String nackingMessage$str() {
        return "SRMSG18212: Message %s from channel '%s' was not sent to Kafka topic '%s' - nacking message";
    }
    @Override
    public final void configServers(final String serverConfig, final String servers) {
        super.log.logf(FQCN, INFO, null, configServers$str(), serverConfig, servers);
    }
    protected String configServers$str() {
        return "SRMSG18213: Setting %s to %s";
    }
    @Override
    public final void keyDeserializerOmitted() {
        super.log.logf(FQCN, INFO, null, keyDeserializerOmitted$str());
    }
    protected String keyDeserializerOmitted$str() {
        return "SRMSG18214: Key deserializer omitted, using String as default";
    }
    @Override
    public final void errorWhileClosingWriteStream(final Throwable t) {
        super.log.logf(FQCN, DEBUG, t, errorWhileClosingWriteStream$str());
    }
    protected String errorWhileClosingWriteStream$str() {
        return "SRMSG18215: An error has been caught while closing the Kafka Write Stream";
    }
    @Override
    public final void noGroupId(final String randomId) {
        super.log.logf(FQCN, WARN, null, noGroupId$str(), randomId);
    }
    protected String noGroupId$str() {
        return "SRMSG18216: No `group.id` set in the configuration, generate a random id: %s";
    }
    @Override
    public final void unableToReadRecord(final Set<String> topics, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToReadRecord$str(), topics);
    }
    protected String unableToReadRecord$str() {
        return "SRMSG18217: Unable to read a record from Kafka topics '%s'";
    }
    @Override
    public final void exceptionOnClose(final Throwable t) {
        super.log.logf(FQCN, DEBUG, t, exceptionOnClose$str());
    }
    protected String exceptionOnClose$str() {
        return "SRMSG18218: An exception has been caught while closing the Kafka consumer";
    }
    @Override
    public final void loadingConsumerRebalanceListenerFromConfiguredName(final String configuredName) {
        super.log.logf(FQCN, INFO, null, loadingConsumerRebalanceListenerFromConfiguredName$str(), configuredName);
    }
    protected String loadingConsumerRebalanceListenerFromConfiguredName$str() {
        return "SRMSG18219: Loading KafkaConsumerRebalanceListener from configured name '%s'";
    }
    @Override
    public final void loadingConsumerRebalanceListenerFromGroupId(final String consumerGroup) {
        super.log.logf(FQCN, INFO, null, loadingConsumerRebalanceListenerFromGroupId$str(), consumerGroup);
    }
    protected String loadingConsumerRebalanceListenerFromGroupId$str() {
        return "SRMSG18220: Loading KafkaConsumerRebalanceListener from group id '%s'";
    }
    @Override
    public final void unableToExecuteConsumerRevokedRebalanceListener(final String consumerGroup, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToExecuteConsumerRevokedRebalanceListener$str(), consumerGroup);
    }
    protected String unableToExecuteConsumerRevokedRebalanceListener$str() {
        return "SRMSG18222: Unable to execute consumer revoked re-balance listener for group '%s'";
    }
    @Override
    public final void executingConsumerRevokedRebalanceListener(final String consumerGroup) {
        super.log.logf(FQCN, INFO, null, executingConsumerRevokedRebalanceListener$str(), consumerGroup);
    }
    protected String executingConsumerRevokedRebalanceListener$str() {
        return "SRMSG18224: Executing consumer revoked re-balance listener for group '%s'";
    }
    @Override
    public final void executedConsumerAssignedRebalanceListener(final String consumerGroup) {
        super.log.logf(FQCN, INFO, null, executedConsumerAssignedRebalanceListener$str(), consumerGroup);
    }
    protected String executedConsumerAssignedRebalanceListener$str() {
        return "SRMSG18225: Executed consumer assigned re-balance listener for group '%s'";
    }
    @Override
    public final void executedConsumerRevokedRebalanceListener(final String consumerGroup) {
        super.log.logf(FQCN, INFO, null, executedConsumerRevokedRebalanceListener$str(), consumerGroup);
    }
    protected String executedConsumerRevokedRebalanceListener$str() {
        return "SRMSG18226: Executed consumer revoked re-balance listener for group '%s'";
    }
    @Override
    public final void reEnablingConsumerForGroup(final String consumerGroup) {
        super.log.logf(FQCN, WARN, null, reEnablingConsumerForGroup$str(), consumerGroup);
    }
    protected String reEnablingConsumerForGroup$str() {
        return "SRMSG18227: Re-enabling consumer for group '%s'. This consumer was paused because of a re-balance failure.";
    }
    @Override
    public final void failureReported(final Set<String> topics, final Throwable t) {
        super.log.logf(FQCN, WARN, t, failureReported$str(), topics);
    }
    protected String failureReported$str() {
        return "SRMSG18228: A failure has been reported for Kafka topics '%s'";
    }
    @Override
    public final void failureReportedDuringRebalance(final Set<String> topics, final Throwable t) {
        super.log.logf(FQCN, WARN, t, failureReportedDuringRebalance$str(), topics);
    }
    protected String failureReportedDuringRebalance$str() {
        return "SRMSG18242: A failure has been reported during a rebalance - the operation will be retried: '%s'";
    }
    @Override
    public final void configuredTopics(final String channel, final Set<String> topics) {
        super.log.logf(FQCN, INFO, null, configuredTopics$str(), channel, topics);
    }
    protected String configuredTopics$str() {
        return "SRMSG18229: Configured topics for channel '%s': %s";
    }
    @Override
    public final void configuredPattern(final String channel, final String pattern) {
        super.log.logf(FQCN, INFO, null, configuredPattern$str(), channel, pattern);
    }
    protected String configuredPattern$str() {
        return "SRMSG18230: Configured topics matching pattern for channel '%s': %s";
    }
    @Override
    public final void waitingForAckForTooLong(final long offset, final TopicPartition topicPartition, final long delay, final long configInMs, final long queueSize, final long lastCommittedOffset) {
        super.log.logf(FQCN, WARN, null, waitingForAckForTooLong$str(), offset, topicPartition, delay, configInMs, queueSize, lastCommittedOffset);
    }
    protected String waitingForAckForTooLong$str() {
        return "SRMSG18231: The record %d from topic-partition '%s' has waited for %s seconds to be acknowledged. This waiting time is greater than the configured threshold (%d ms). At the moment %d messages from this partition are awaiting acknowledgement. The last committed offset for this partition was %d. This error is due to a potential issue in the application which does not acknowledged the records in a timely fashion. The connector cannot commit as a record processing has not completed.";
    }
    @Override
    public final void settingCommitInterval(final String group, final long commitInterval) {
        super.log.logf(FQCN, DEBUG, null, settingCommitInterval$str(), group, commitInterval);
    }
    protected String settingCommitInterval$str() {
        return "SRMSG18232: Will commit for group '%s' every %d milliseconds.";
    }
    @Override
    public final void invalidValueSerializerForStructuredCloudEvent(final String serializer) {
        super.log.logf(FQCN, ERROR, null, invalidValueSerializerForStructuredCloudEvent$str(), serializer);
    }
    protected String invalidValueSerializerForStructuredCloudEvent$str() {
        return "SRMSG18233: Invalid value serializer to write a structured Cloud Event. Found %s, expected the org.apache.kafka.common.serialization.StringSerializer";
    }
    @Override
    public final void disableAutoCommit(final String channel) {
        super.log.logf(FQCN, DEBUG, null, disableAutoCommit$str(), channel);
    }
    protected String disableAutoCommit$str() {
        return "SRMSG18234: Auto-commit disabled for channel %s";
    }
    @Override
    public final void disableThrottledCommitStrategyHealthCheck(final String group) {
        super.log.logf(FQCN, WARN, null, disableThrottledCommitStrategyHealthCheck$str(), group);
    }
    protected String disableThrottledCommitStrategyHealthCheck$str() {
        return "SRMSG18235: Will not health check throttled commit strategy for group '%s'.";
    }
    @Override
    public final void setThrottledCommitStrategyReceivedRecordMaxAge(final String group, final long unprocessedRecordMaxAge) {
        super.log.logf(FQCN, DEBUG, null, setThrottledCommitStrategyReceivedRecordMaxAge$str(), group, unprocessedRecordMaxAge);
    }
    protected String setThrottledCommitStrategyReceivedRecordMaxAge$str() {
        return "SRMSG18236: Will mark throttled commit strategy for group '%s' as unhealthy if records go more than %d milliseconds without being processed.";
    }
    @Override
    public final void setKafkaProducerClientId(final String name) {
        super.log.logf(FQCN, DEBUG, null, setKafkaProducerClientId$str(), name);
    }
    protected String setKafkaProducerClientId$str() {
        return "SRMSG18237: Setting client.id for Kafka producer to %s";
    }
    @Override
    public final void acknowledgementFromRevokedTopicPartition(final long offset, final TopicPartition topicPartition, final String groupId, final Collection<TopicPartition> assignments) {
        super.log.logf(FQCN, DEBUG, null, acknowledgementFromRevokedTopicPartition$str(), offset, topicPartition, groupId, assignments);
    }
    protected String acknowledgementFromRevokedTopicPartition$str() {
        return "SRMSG18239: Received acknowledgement for record %d on '%s' (consumer group: '%s'). Ignoring it because the partition is not assigned to the consume anymore. Record will likely be processed again. Current assignments are %s.";
    }
    @Override
    public final void commitStrategyForChannel(final String strategy, final String channel) {
        super.log.logf(FQCN, DEBUG, null, commitStrategyForChannel$str(), strategy, channel);
    }
    protected String commitStrategyForChannel$str() {
        return "SRMSG18240: '%s' commit strategy used for channel '%s'";
    }
    @Override
    public final void deserializationFailureHandlerFailure(final String instance, final Throwable t) {
        super.log.logf(FQCN, FATAL, t, deserializationFailureHandlerFailure$str(), instance);
    }
    protected String deserializationFailureHandlerFailure$str() {
        return "SRMSG18241: The deserialization failure handler `%s` throws an exception";
    }
    @Override
    public final void pauseAllPartitionOnTermination() {
        super.log.logf(FQCN, DEBUG, null, pauseAllPartitionOnTermination$str());
    }
    protected String pauseAllPartitionOnTermination$str() {
        return "SRMSG18243: Shutting down - Pausing all topic-partitions";
    }
    @Override
    public final void waitingForMessageProcessing(final long p) {
        super.log.logf(FQCN, DEBUG, null, waitingForMessageProcessing$str(), p);
    }
    protected String waitingForMessageProcessing$str() {
        return "SRMSG18244: Shutting down - Waiting for message processing to complete, %d messages still in processing";
    }
    @Override
    public final void messageStillUnprocessedAfterTimeout(final long unprocessed) {
        super.log.logf(FQCN, WARN, null, messageStillUnprocessedAfterTimeout$str(), unprocessed);
    }
    protected String messageStillUnprocessedAfterTimeout$str() {
        return "SRMSG18245: There are still %d unprocessed messages after the closing timeout";
    }
    @Override
    public final void pausingChannel(final String channel, final String clientId, final int queueSize, final int maxQueueSize) {
        super.log.logf(FQCN, DEBUG, null, pausingChannel$str(), channel, clientId, queueSize, maxQueueSize);
    }
    protected String pausingChannel$str() {
        return "SRMSG18246: Pausing Kafka consumption for client %s (%s), queue size %s >= %s";
    }
    @Override
    public final void resumingChannel(final String channel, final String clientId, final int queueSize, final int halfMaxQueueSize) {
        super.log.logf(FQCN, DEBUG, null, resumingChannel$str(), channel, clientId, queueSize, halfMaxQueueSize);
    }
    protected String resumingChannel$str() {
        return "SRMSG18247: Resuming Kafka consumption for channel %s (%s), queue size %s <= %s";
    }
    @Override
    public final void keySerializerOmitted() {
        super.log.logf(FQCN, DEBUG, null, keySerializerOmitted$str());
    }
    protected String keySerializerOmitted$str() {
        return "SRMSG18248: Key serializer omitted, using String as default";
    }
    @Override
    public final void unableToDeserializeMessage(final String topic, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToDeserializeMessage$str(), topic);
    }
    protected String unableToDeserializeMessage$str() {
        return "SRMSG18249: Unable to recover from the deserialization failure (topic: %s), configure a DeserializationFailureHandler to recover from errors.";
    }
    @Override
    public final void deprecatedConfig(final String deprecated, final String replace) {
        super.log.logf(FQCN, WARN, null, deprecatedConfig$str(), deprecated, replace);
    }
    protected String deprecatedConfig$str() {
        return "SRMSG18250: The configuration property '%s' is deprecated and is replaced by '%s'.";
    }
    @Override
    public final void committed(final Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets) {
        super.log.logf(FQCN, DEBUG, null, committed$str(), offsets);
    }
    protected String committed$str() {
        return "SRMSG18251: Committed %s";
    }
    @Override
    public final void failedToCommit(final Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets, final Throwable failure) {
        super.log.logf(FQCN, WARN, failure, failedToCommit$str(), offsets);
    }
    protected String failedToCommit$str() {
        return "SRMSG18252: Failed to commit %s, it will be re-attempted";
    }
    @Override
    public final void removingPartitionFromStore(final TopicPartition tp, final Collection<TopicPartition> assignments) {
        super.log.logf(FQCN, DEBUG, null, removingPartitionFromStore$str(), tp, assignments);
    }
    protected String removingPartitionFromStore$str() {
        return "SRMSG18253: Removing topic-partition '%s' from the store - the partition is not assigned to the consumer anymore. Current assignments are: %s";
    }
    @Override
    public final void partitionRevokedCollectingRecordsToCommit(final TopicPartition partition, final long commit) {
        super.log.logf(FQCN, DEBUG, null, partitionRevokedCollectingRecordsToCommit$str(), partition, commit);
    }
    protected String partitionRevokedCollectingRecordsToCommit$str() {
        return "SRMSG18254: Topic-partition '%s' has been revoked - going to commit offset %d";
    }
    @Override
    public final void receivedOutdatedOffset(final TopicPartition topicPartition, final long offset, final long lastCommitted) {
        super.log.logf(FQCN, DEBUG, null, receivedOutdatedOffset$str(), topicPartition, offset, lastCommitted);
    }
    protected String receivedOutdatedOffset$str() {
        return "SRMSG18255: Received a record from topic-partition '%s' at offset %d, while the last committed offset is %d - Ignoring record";
    }
    @Override
    public final void initializeStoreAtPosition(final TopicPartition topicPartition, final long position) {
        super.log.logf(FQCN, INFO, null, initializeStoreAtPosition$str(), topicPartition, position);
    }
    protected String initializeStoreAtPosition$str() {
        return "SRMSG18256: Initialize record store for topic-partition '%s' at position %d.";
    }
    @Override
    public final void connectedToKafka(final String id, final String bootstrapServers, final String consumerGroup, final Set<String> topics) {
        super.log.logf(FQCN, INFO, null, connectedToKafka4$str(), id, bootstrapServers, consumerGroup, topics);
    }
    protected String connectedToKafka4$str() {
        return "SRMSG18257: Kafka consumer %s, connected to Kafka brokers '%s', belongs to the '%s' consumer group and is configured to poll records from %s";
    }
    @Override
    public final void connectedToKafka(final String id, final String bootstrapServers, final String topic) {
        super.log.logf(FQCN, INFO, null, connectedToKafka3$str(), id, bootstrapServers, topic);
    }
    protected String connectedToKafka3$str() {
        return "SRMSG18258: Kafka producer %s, connected to Kafka brokers '%s', is configured to write records to '%s'";
    }
    @Override
    public final void failedToCommitAsync(final TopicPartition topicPartition, final long offset, final Throwable cause) {
        super.log.logf(FQCN, WARN, cause, failedToCommitAsync$str(), topicPartition, offset);
    }
    protected String failedToCommitAsync$str() {
        return "SRMSG18259: Kafka latest commit strategy failed to commit record from topic-partition '%s' at offset %d";
    }
    @Override
    public final void unableToSerializeMessage(final String topic, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToSerializeMessage$str(), topic);
    }
    protected String unableToSerializeMessage$str() {
        return "SRMSG18260: Unable to recover from the serialization failure (topic: %s), configure a SerializationFailureHandler to recover from errors.";
    }
    @Override
    public final void unableToInitializeProducer(final String channel, final Throwable t) {
        super.log.logf(FQCN, ERROR, t, unableToInitializeProducer$str(), channel);
    }
    protected String unableToInitializeProducer$str() {
        return "SRMSG18261: Unable to initialize producer from channel %s.";
    }
    @Override
    public final void transactionAborted(final String producerId, final String channel) {
        super.log.logf(FQCN, WARN, null, transactionAborted$str(), producerId, channel);
    }
    protected String transactionAborted$str() {
        return "SRMSG18262: Aborting transaction for producer id %s in channel %s.";
    }
    @Override
    public final void serializationFailureHandlerFailure(final String instance, final Throwable t) {
        super.log.logf(FQCN, FATAL, t, serializationFailureHandlerFailure$str(), instance);
    }
    protected String serializationFailureHandlerFailure$str() {
        return "SRMSG18263: The serialization failure handler `%s` throws an exception";
    }
    @Override
    public final void checkpointDefaultStateStore() {
        super.log.logf(FQCN, WARN, null, checkpointDefaultStateStore$str());
    }
    protected String checkpointDefaultStateStore$str() {
        return "SRMSG18264: No `checkpoint.state-store` given to use with checkpoint commit strategy. `file` will be used.";
    }
    @Override
    public final void disableCheckpointCommitStrategyHealthCheck(final String consumerId) {
        super.log.logf(FQCN, WARN, null, disableCheckpointCommitStrategyHealthCheck$str(), consumerId);
    }
    protected String disableCheckpointCommitStrategyHealthCheck$str() {
        return "SRMSG18265: Will not health check checkpoint commit strategy for consumer '%s'.";
    }
    @Override
    public final void setCheckpointCommitStrategyUnsyncedStateMaxAge(final String consumerId, final int unsyncedStateMaxAge) {
        super.log.logf(FQCN, DEBUG, null, setCheckpointCommitStrategyUnsyncedStateMaxAge$str(), consumerId, unsyncedStateMaxAge);
    }
    protected String setCheckpointCommitStrategyUnsyncedStateMaxAge$str() {
        return "SRMSG18266: Will mark checkpoint commit strategy for consumer '%s' as unhealthy if processing state go more than %d milliseconds without being persisted.";
    }
    @Override
    public final void checkpointPartitionsAssigned(final String consumerId, final Collection<TopicPartition> assignments, final String state) {
        super.log.logf(FQCN, DEBUG, null, checkpointPartitionsAssigned$str(), consumerId, assignments, state);
    }
    protected String checkpointPartitionsAssigned$str() {
        return "SRMSG18267: Partitions assigned to client %s : %s with initial state %s";
    }
    @Override
    public final void failedCheckpointPartitionsAssigned(final String consumerId, final Collection<TopicPartition> assignments, final Throwable cause) {
        super.log.logf(FQCN, ERROR, cause, failedCheckpointPartitionsAssigned$str(), consumerId, assignments);
    }
    protected String failedCheckpointPartitionsAssigned$str() {
        return "SRMSG18268: Failed fetching state on partitions assigned to client %s : %s";
    }
    @Override
    public final void checkpointPartitionsRevoked(final String consumerId, final Collection<TopicPartition> assignments, final String state) {
        super.log.logf(FQCN, DEBUG, null, checkpointPartitionsRevoked$str(), consumerId, assignments, state);
    }
    protected String checkpointPartitionsRevoked$str() {
        return "SRMSG18269: Partitions revoked from client %s: %s with state to persist %s";
    }
    @Override
    public final void checkpointPersistedState(final String consumerId, final String state) {
        super.log.logf(FQCN, DEBUG, null, checkpointPersistedState$str(), consumerId, state);
    }
    protected String checkpointPersistedState$str() {
        return "SRMSG18270: Persisted state for client %s : %s";
    }
    @Override
    public final void checkpointFailedPersistingState(final String consumerId, final String state, final Throwable cause) {
        super.log.logf(FQCN, WARN, cause, checkpointFailedPersistingState$str(), consumerId, state);
    }
    protected String checkpointFailedPersistingState$str() {
        return "SRMSG18271: Failed persisting state for %s : %s";
    }
    @Override
    public final void checkpointFailedPersistingStateRetryable(final String consumerId, final String state, final Throwable cause) {
        super.log.logf(FQCN, DEBUG, cause, checkpointFailedPersistingStateRetryable$str(), consumerId, state);
    }
    protected String checkpointFailedPersistingStateRetryable$str() {
        return "SRMSG18272: Failed persisting state but can be retried for %s : %s";
    }
    @Override
    public final void checkpointStateTypeNotFound(final String channel, final String fqcn) {
        super.log.logf(FQCN, WARN, null, checkpointStateTypeNotFound$str(), channel, fqcn);
    }
    protected String checkpointStateTypeNotFound$str() {
        return "SRMSG18273: Checkpoint commit strategy processing state type not found for channel %s : %s";
    }
    @Override
    public final void interceptorOnSendError(final String channel, final Throwable cause) {
        super.log.logf(FQCN, INFO, cause, interceptorOnSendError$str(), channel);
    }
    protected String interceptorOnSendError$str() {
        return "SRMSG18274: Error caught in producer interceptor `onSend` for channel %s";
    }
    @Override
    public final void interceptorOnAcknowledgeError(final String channel, final Throwable cause) {
        super.log.logf(FQCN, INFO, cause, interceptorOnAcknowledgeError$str(), channel);
    }
    protected String interceptorOnAcknowledgeError$str() {
        return "SRMSG18275: Error caught in producer interceptor `onAcknowledge` for channel %s";
    }
    @Override
    public final void interceptorCloseError(final String channel, final Throwable cause) {
        super.log.logf(FQCN, TRACE, cause, interceptorCloseError$str(), channel);
    }
    protected String interceptorCloseError$str() {
        return "SRMSG18276: Error caught in producer interceptor `close` for channel %s";
    }
    @Override
    public final void delayedRetryTopic(final String channel, final Collection<String> retryTopics, final int maxRetries, final long timeout, final String deadLetterTopic) {
        super.log.logf(FQCN, INFO, null, delayedRetryTopic$str(), channel, retryTopics, maxRetries, timeout, deadLetterTopic);
    }
    protected String delayedRetryTopic$str() {
        return "SRMSG18277: Delayed retry topics configured for channel `%s` with topics `%s`, max retries, `%d`, timeout `%d`ms, dlq topic `%s`";
    }
    @Override
    public final void delayedRetryNack(final String channel, final String topic) {
        super.log.logf(FQCN, INFO, null, delayedRetryNack$str(), channel, topic);
    }
    protected String delayedRetryNack$str() {
        return "SRMSG18278: A message sent to channel `%s` has been nacked, sending the record to topic %s";
    }
    @Override
    public final void delayedRetryTimeout(final String channel, final long timeout, final String record) {
        super.log.logf(FQCN, WARN, null, delayedRetryTimeout$str(), channel, timeout, record);
    }
    protected String delayedRetryTimeout$str() {
        return "SRMSG18279: A message sent to channel `%s` reached delayed retry timeout `%s` milliseconds reached for record `%s`";
    }
    @Override
    public final void delayedRetryNoDlq(final String channel) {
        super.log.logf(FQCN, WARN, null, delayedRetryNoDlq$str(), channel);
    }
    protected String delayedRetryNoDlq$str() {
        return "SRMSG18280: A message sent to channel `%s` has been nacked and won't be retried again. Configure `dead-letter-queue.topic` for sending the record to a dead letter topic";
    }
    @Override
    public final void topicsConfigurationIgnored(final String topics, final String channel, final String topicPartitions) {
        super.log.logf(FQCN, INFO, null, topicsConfigurationIgnored$str(), topics, channel, topicPartitions);
    }
    protected String topicsConfigurationIgnored$str() {
        return "SRMSG18281: The topics configuration `%s` for channel `%s` will be ignored because the consumer will be assigned to partitions %s";
    }
    @Override
    public final void requestReplyRecordIgnored(final String channel, final String replyTopic, final String correlationId) {
        super.log.logf(FQCN, DEBUG, null, requestReplyRecordIgnored$str(), channel, replyTopic, correlationId);
    }
    protected String requestReplyRecordIgnored$str() {
        return "SRMSG18282: Ignored reply from channel `%s` to topic `%s` with correlation id %s";
    }
    @Override
    public final void requestReplyConsumerFailure(final String channel, final String replyTopic, final Throwable throwable) {
        super.log.logf(FQCN, ERROR, throwable, requestReplyConsumerFailure$str(), channel, replyTopic);
    }
    protected String requestReplyConsumerFailure$str() {
        return "SRMSG18283: Failure from channel `%s` request/reply consumer for topic `%s`";
    }
    @Override
    public final void transactionCommitFailed(final Throwable throwable) {
        super.log.logf(FQCN, WARN, throwable, transactionCommitFailed$str());
    }
    protected String transactionCommitFailed$str() {
        return "SRMSG18284: Transaction commit failed for channel, aborting the transaction";
    }
    @Override
    public final void pollFailureRetry(final String clientId, final String channel, final Throwable throwable) {
        super.log.logf(FQCN, WARN, throwable, pollFailureRetry$str(), clientId, channel);
    }
    protected String pollFailureRetry$str() {
        return "SRMSG18285: Failed to poll consumer client `%s` for channel `%s`, it will be re-attempted";
    }
    @Override
    public final void shareGroupAcknowledgementCommitted(final String offsets) {
        super.log.logf(FQCN, DEBUG, null, shareGroupAcknowledgementCommitted$str(), offsets);
    }
    protected String shareGroupAcknowledgementCommitted$str() {
        return "SRMSG18286: Share group acknowledgement committed for offsets: %s";
    }
    @Override
    public final void shareGroupAcknowledgementCommitFailed(final Throwable throwable) {
        super.log.logf(FQCN, WARN, throwable, shareGroupAcknowledgementCommitFailed$str());
    }
    protected String shareGroupAcknowledgementCommitFailed$str() {
        return "SRMSG18287: Share group acknowledgement commit failed";
    }
    @Override
    public final void shareGroupPendingOffsets(final String topicPartition, final String pendingSet) {
        super.log.logf(FQCN, DEBUG, null, shareGroupPendingOffsets$str(), topicPartition, pendingSet);
    }
    protected String shareGroupPendingOffsets$str() {
        return "SRMSG18288: Pending share group offsets for %s: %s";
    }
    @Override
    public final void shareGroupProcessingTimeout(final String topicPartition, final long offset, final long elapsedSeconds, final long timeoutMs) {
        super.log.logf(FQCN, WARN, null, shareGroupProcessingTimeout$str(), topicPartition, offset, elapsedSeconds, timeoutMs);
    }
    protected String shareGroupProcessingTimeout$str() {
        return "SRMSG18289: Share group record from topic-partition '%s' at offset %d has been processing for %d seconds, exceeding the timeout of %d ms. Reporting failure.";
    }
    @Override
    public final void shareGroupRenewingLocks(final int count) {
        super.log.logf(FQCN, DEBUG, null, shareGroupRenewingLocks$str(), count);
    }
    protected String shareGroupRenewingLocks$str() {
        return "SRMSG18290: Renewed share group acquisition locks for %d in-progress records";
    }
    @Override
    public final void shareGroupIncompatibleConfiguration(final String properties, final String channel) {
        super.log.logf(FQCN, WARN, null, shareGroupIncompatibleConfiguration$str(), properties, channel);
    }
    protected String shareGroupIncompatibleConfiguration$str() {
        return "SRMSG18291: The `%s` configuration is not supported with share groups on channel `%s` and will be ignored";
    }
    @Override
    public final void pooledProducerCreated(final int index, final String channel, final String transactionalId) {
        super.log.logf(FQCN, INFO, null, pooledProducerCreated$str(), index, channel, transactionalId);
    }
    protected String pooledProducerCreated$str() {
        return "SRMSG18292: Pooled Kafka producer %d created on channel %s with transactional.id %s";
    }
    @Override
    public final void closingPooledProducers(final int count, final String channel) {
        super.log.logf(FQCN, DEBUG, null, closingPooledProducers$str(), count, channel);
    }
    protected String closingPooledProducers$str() {
        return "SRMSG18293: Closing %d pooled producers for channel %s";
    }
    @Override
    public final void throttledOrderedDeprecated() {
        super.log.logf(FQCN, WARN, null, throttledOrderedDeprecated$str());
    }
    protected String throttledOrderedDeprecated$str() {
        return "SRMSG18294: Configuration 'throttled.ordered' is deprecated. Use 'ordered' instead, which works with any commit strategy.";
    }
    @Override
    public final void pooledProducerEvicted(final String channel, final Object transactionalId) {
        super.log.logf(FQCN, WARN, null, pooledProducerEvicted$str(), channel, transactionalId);
    }
    protected String pooledProducerEvicted$str() {
        return "SRMSG18295: Evicting broken pooled producer from channel %s with transactional.id %s";
    }
}
