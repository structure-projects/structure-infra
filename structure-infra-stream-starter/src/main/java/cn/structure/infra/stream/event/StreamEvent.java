package cn.structure.infra.stream.event;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class StreamEvent<T> {

    private String eventId;
    private String eventType;
    private String businessType;
    private String source;
    private LocalDateTime timestamp;
    private T payload;
    private Map<String, String> headers = new HashMap<>();
    private String traceId;

    public StreamEvent() {
    }

    public StreamEvent(String eventId, String eventType, String businessType, String source,
                       LocalDateTime timestamp, T payload, Map<String, String> headers, String traceId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.businessType = businessType;
        this.source = source;
        this.timestamp = timestamp;
        this.payload = payload;
        this.headers = headers != null ? headers : new HashMap<>();
        this.traceId = traceId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new HashMap<>();
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public static <T> StreamEvent<T> of(String eventType, T payload) {
        return new StreamEvent<>(
                java.util.UUID.randomUUID().toString(),
                eventType,
                null,
                null,
                LocalDateTime.now(),
                payload,
                new HashMap<>(),
                null
        );
    }

    public static <T> StreamEvent<T> of(String eventType, String businessType, T payload) {
        return new StreamEvent<>(
                java.util.UUID.randomUUID().toString(),
                eventType,
                businessType,
                null,
                LocalDateTime.now(),
                payload,
                new HashMap<>(),
                null
        );
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String eventId;
        private String eventType;
        private String businessType;
        private String source;
        private LocalDateTime timestamp;
        private T payload;
        private Map<String, String> headers = new HashMap<>();
        private String traceId;

        public Builder<T> eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder<T> eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder<T> businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public Builder<T> source(String source) {
            this.source = source;
            return this;
        }

        public Builder<T> timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Builder<T> headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder<T> traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public StreamEvent<T> build() {
            return new StreamEvent<>(eventId, eventType, businessType, source, timestamp, payload, headers, traceId);
        }
    }
}
