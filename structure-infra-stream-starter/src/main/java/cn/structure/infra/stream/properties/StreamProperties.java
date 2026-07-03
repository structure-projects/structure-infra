package cn.structure.infra.stream.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "structure.infra.stream")
public class StreamProperties {

    private boolean enabled = true;
    private boolean autoBinding = true;
    private String defaultGroup = "default";
    private String defaultContentType = "application/json";
    private String defaultBinder;
    private Integer defaultConcurrency = 1;
    private Map<String, Binding> bindings = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoBinding() {
        return autoBinding;
    }

    public void setAutoBinding(boolean autoBinding) {
        this.autoBinding = autoBinding;
    }

    public String getDefaultGroup() {
        return defaultGroup;
    }

    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    public String getDefaultContentType() {
        return defaultContentType;
    }

    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }

    public String getDefaultBinder() {
        return defaultBinder;
    }

    public void setDefaultBinder(String defaultBinder) {
        this.defaultBinder = defaultBinder;
    }

    public Integer getDefaultConcurrency() {
        return defaultConcurrency;
    }

    public void setDefaultConcurrency(Integer defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public Map<String, Binding> getBindings() {
        return bindings;
    }

    public void setBindings(Map<String, Binding> bindings) {
        this.bindings = bindings;
    }

    public Binding getBinding(String bindingName) {
        return bindings.computeIfAbsent(bindingName, k -> new Binding());
    }

    public static class Binding {
        private String destination;
        private String contentType = "application/json";
        private String group;
        private String binder;
        private Integer concurrency;
        private String consumerPrefix = "consumer";
        private String producerPrefix = "producer";

        public Binding() {
        }

        public Binding(String destination) {
            this.destination = destination;
        }

        public Binding(String destination, String group) {
            this.destination = destination;
            this.group = group;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getBinder() {
            return binder;
        }

        public void setBinder(String binder) {
            this.binder = binder;
        }

        public Integer getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(Integer concurrency) {
            this.concurrency = concurrency;
        }

        public String getConsumerPrefix() {
            return consumerPrefix;
        }

        public void setConsumerPrefix(String consumerPrefix) {
            this.consumerPrefix = consumerPrefix;
        }

        public String getProducerPrefix() {
            return producerPrefix;
        }

        public void setProducerPrefix(String producerPrefix) {
            this.producerPrefix = producerPrefix;
        }
    }
}
