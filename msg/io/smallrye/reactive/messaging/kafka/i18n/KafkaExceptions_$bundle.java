package io.smallrye.reactive.messaging.kafka.i18n;

import java.util.Locale;
import java.lang.IllegalStateException;
import java.io.Serializable;
import org.eclipse.microprofile.reactive.messaging.Message;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import java.lang.String;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import java.lang.Throwable;
import java.lang.Class;
import java.util.Arrays;
import java.lang.IllegalArgumentException;
import java.util.NoSuchElementException;
import java.lang.UnsupportedOperationException;

/**
 * Warning this class consists of generated code.
 */
public class KafkaExceptions_$bundle implements KafkaExceptions, Serializable {
    private static final long serialVersionUID = 1L;
    protected KafkaExceptions_$bundle() {}
    public static final KafkaExceptions_$bundle INSTANCE = new KafkaExceptions_$bundle();
    protected Object readResolve() {
        return INSTANCE;
    }
    private static final Locale LOCALE = Locale.ROOT;
    protected Locale getLoggingLocale() {
        return LOCALE;
    }
    protected String illegalArgumentNoMetadata$str() {
        return "SRMSG18000: `message` does not contain metadata of class %s";
    }
    @Override
    public final IllegalArgumentException illegalArgumentNoMetadata(final Class c) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), illegalArgumentNoMetadata$str(), c));
        _copyStackTraceMinusOne(result);
        return result;
    }
    private static void _copyStackTraceMinusOne(final Throwable e) {
        final StackTraceElement[] st = e.getStackTrace();
        if (st.length > 0) e.setStackTrace(Arrays.copyOfRange(st, 1, st.length));
    }
    protected String illegalArgumentUnknownFailureStrategy$str() {
        return "SRMSG18001: Unknown failure strategy: %s";
    }
    @Override
    public final IllegalArgumentException illegalArgumentUnknownFailureStrategy(final String strategy) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), illegalArgumentUnknownFailureStrategy$str(), strategy));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String illegalStateConsumeWithoutBackPressure$str() {
        return "SRMSG18002: Expecting downstream to consume without back-pressure";
    }
    @Override
    public final IllegalStateException illegalStateConsumeWithoutBackPressure() {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), illegalStateConsumeWithoutBackPressure$str()));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String illegalStateOnlyOneSubscriber$str() {
        return "SRMSG18003: Only one subscriber allowed";
    }
    @Override
    public final IllegalStateException illegalStateOnlyOneSubscriber() {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), illegalStateOnlyOneSubscriber$str()));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String illegalArgumentInvalidFailureStrategy$str() {
        return "SRMSG18004: Invalid failure strategy: %s";
    }
    @Override
    public final IllegalArgumentException illegalArgumentInvalidFailureStrategy(final String strategy) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), illegalArgumentInvalidFailureStrategy$str(), strategy));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String illegalArgumentUnknownCommitStrategy$str() {
        return "SRMSG18005: Unknown commit strategy: %s";
    }
    @Override
    public final IllegalArgumentException illegalArgumentUnknownCommitStrategy(final String strategy) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), illegalArgumentUnknownCommitStrategy$str(), strategy));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String illegalArgumentInvalidCommitStrategy$str() {
        return "SRMSG18006: Invalid commit strategy: %s";
    }
    @Override
    public final IllegalArgumentException illegalArgumentInvalidCommitStrategy(final String strategy) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), illegalArgumentInvalidCommitStrategy$str(), strategy));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindRebalanceListener2$str() {
        return "SRMSG18007: Unable to find the KafkaConsumerRebalanceListener named `%s` for channel `%s`";
    }
    @Override
    public final UnsatisfiedResolutionException unableToFindRebalanceListener(final String name, final String channel) {
        final UnsatisfiedResolutionException result = new UnsatisfiedResolutionException(String.format(getLoggingLocale(), unableToFindRebalanceListener2$str(), name, channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindRebalanceListener3$str() {
        return "SRMSG18008: Unable to select the KafkaConsumerRebalanceListener named `%s` for channel `%s` - too many matches (%d)";
    }
    @Override
    public final AmbiguousResolutionException unableToFindRebalanceListener(final String name, final String channel, final int count) {
        final AmbiguousResolutionException result = new AmbiguousResolutionException(String.format(getLoggingLocale(), unableToFindRebalanceListener3$str(), name, channel, count));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String missingValueDeserializer$str() {
        return "SRMSG18009: Cannot configure the Kafka consumer for channel `%s` - the `mp.messaging.incoming.%s.value.deserializer` property is missing";
    }
    @Override
    public final IllegalArgumentException missingValueDeserializer(final String channel, final String channelAgain) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), missingValueDeserializer$str(), channel, channelAgain));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToCreateInstance$str() {
        return "SRMSG18010: Unable to create an instance of `%s`";
    }
    @Override
    public final IllegalArgumentException unableToCreateInstance(final String clazz, final Throwable cause) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), unableToCreateInstance$str(), clazz), cause);
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindDeserializationFailureHandler2$str() {
        return "SRMSG18011: Unable to find the DeserializationFailureHandler named `%s` for channel `%s`";
    }
    @Override
    public final UnsatisfiedResolutionException unableToFindDeserializationFailureHandler(final String name, final String channel) {
        final UnsatisfiedResolutionException result = new UnsatisfiedResolutionException(String.format(getLoggingLocale(), unableToFindDeserializationFailureHandler2$str(), name, channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindDeserializationFailureHandler3$str() {
        return "SRMSG18012: Unable to select the DeserializationFailureHandler named `%s` for channel `%s` - too many matches (%d)";
    }
    @Override
    public final AmbiguousResolutionException unableToFindDeserializationFailureHandler(final String name, final String channel, final int count) {
        final AmbiguousResolutionException result = new AmbiguousResolutionException(String.format(getLoggingLocale(), unableToFindDeserializationFailureHandler3$str(), name, channel, count));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String missingValueSerializer$str() {
        return "SRMSG18013: Cannot configure the Kafka producer for channel `%s` - the `mp.messaging.outgoing.%s.value.serializer` property is missing";
    }
    @Override
    public final IllegalArgumentException missingValueSerializer(final String channel, final String channelAgain) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), missingValueSerializer$str(), channel, channelAgain));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String missingProperty$str() {
        return "SRMSG18014: The config property '%s' is required but it could not be found in any config source";
    }
    @Override
    public final NoSuchElementException missingProperty(final String propertyName) {
        final NoSuchElementException result = new NoSuchElementException(String.format(getLoggingLocale(), missingProperty$str(), propertyName));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String cannotConvertProperty$str() {
        return "SRMSG18015: Cannot convert property '%s' of type %s to %s";
    }
    @Override
    public final NoSuchElementException cannotConvertProperty(final String propertyName, final Class<?> type, final Class<?> targetType) {
        final NoSuchElementException result = new NoSuchElementException(String.format(getLoggingLocale(), cannotConvertProperty$str(), propertyName, type, targetType));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindSerializationFailureHandler2$str() {
        return "SRMSG18016: Unable to find the SerializationFailureHandler named `%s` for channel `%s`";
    }
    @Override
    public final UnsatisfiedResolutionException unableToFindSerializationFailureHandler(final String name, final String channel) {
        final UnsatisfiedResolutionException result = new UnsatisfiedResolutionException(String.format(getLoggingLocale(), unableToFindSerializationFailureHandler2$str(), name, channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindSerializationFailureHandler3$str() {
        return "SRMSG18017: Unable to select the SerializationFailureHandler named `%s` for channel `%s` - too many matches (%d)";
    }
    @Override
    public final AmbiguousResolutionException unableToFindSerializationFailureHandler(final String name, final String channel, final int count) {
        final AmbiguousResolutionException result = new AmbiguousResolutionException(String.format(getLoggingLocale(), unableToFindSerializationFailureHandler3$str(), name, channel, count));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String unableToFindConsumerForChannel$str() {
        return "SRMSG18018: Unable to find the Kafka consumer for channel `%s`";
    }
    @Override
    public final IllegalStateException unableToFindConsumerForChannel(final String channel) {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), unableToFindConsumerForChannel$str(), channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String noKafkaMetadataFound$str() {
        return "SRMSG18019: Unable to find Kafka metadata in message `%s`";
    }
    @Override
    public final IllegalArgumentException noKafkaMetadataFound(final Message<?> message) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), noKafkaMetadataFound$str(), message));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String transactionInProgress$str() {
        return "SRMSG18020: A transaction is already in progress for channel `%s`";
    }
    @Override
    public final IllegalStateException transactionInProgress(final String channel) {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), transactionInProgress$str(), channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String exactlyOnceProcessingNotSupported$str() {
        return "SRMSG18021: Exactly-once processing is not supported on channels with multiple partitions `%s`";
    }
    @Override
    public final IllegalStateException exactlyOnceProcessingNotSupported(final String channel) {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), exactlyOnceProcessingNotSupported$str(), channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String invalidTopics$str() {
        return "SRMSG18022: The Kafka incoming configuration for channel `%s` cannot use `topics` and `%s` at the same time";
    }
    @Override
    public final IllegalArgumentException invalidTopics(final String channel, final String configKey) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), invalidTopics$str(), channel, configKey));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String invalidAssignSeek$str() {
        return "SRMSG18023: Invalid Kafka incoming configuration for channel `%s`, `assign-seek` portion `%s` is invalid. It must respect the format `<topic>:|<partition>|:<offset>`.";
    }
    @Override
    public final IllegalArgumentException invalidAssignSeek(final String channel, final String assignSeek, final Throwable throwable) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), invalidAssignSeek$str(), channel, assignSeek), throwable);
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String invalidAssignSeekTopic$str() {
        return "SRMSG18024: Invalid Kafka incoming configuration for channel `%s`, `assign-seek` portion `%s` is invalid. If topic portion is not present, a single `topic` configuration is needed.";
    }
    @Override
    public final IllegalArgumentException invalidAssignSeekTopic(final String channel, final String assignSeek) {
        final IllegalArgumentException result = new IllegalArgumentException(String.format(getLoggingLocale(), invalidAssignSeekTopic$str(), channel, assignSeek));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String exactlyOnceProcessingRebalance$str() {
        return "SRMSG18025: Partition rebalance during exactly-once processing for channel `%s`: current consumer group metadata: %s, generation id for message: %s";
    }
    @Override
    public final IllegalStateException exactlyOnceProcessingRebalance(final String channel, final String groupMetadata, final String generationId) {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), exactlyOnceProcessingRebalance$str(), channel, groupMetadata, generationId));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String pooledProducerUnsupportedOperation$str() {
        return "SRMSG18026: Pooled producer does not support `%s`. Use transactionScope().";
    }
    @Override
    public final UnsupportedOperationException pooledProducerUnsupportedOperation(final String operation) {
        final UnsupportedOperationException result = new UnsupportedOperationException(String.format(getLoggingLocale(), pooledProducerUnsupportedOperation$str(), operation));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String pooledProducerPoolExhausted$str() {
        return "SRMSG18027: Pooled producer pool exhausted (max-pool-size=%d) on channel `%s`. Increase pooled-producer.max-pool-size or reduce concurrent transaction scopes.";
    }
    @Override
    public final IllegalStateException pooledProducerPoolExhausted(final int maxPoolSize, final String channel) {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), pooledProducerPoolExhausted$str(), maxPoolSize, channel));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String pooledProducerClosed$str() {
        return "SRMSG18028: Pooled producer is closed";
    }
    @Override
    public final IllegalStateException pooledProducerClosed() {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), pooledProducerClosed$str()));
        _copyStackTraceMinusOne(result);
        return result;
    }
    protected String transactionNotStarted$str() {
        return "SRMSG18029: Transaction not started. Call beginTransaction() first.";
    }
    @Override
    public final IllegalStateException transactionNotStarted() {
        final IllegalStateException result = new IllegalStateException(String.format(getLoggingLocale(), transactionNotStarted$str()));
        _copyStackTraceMinusOne(result);
        return result;
    }
}
