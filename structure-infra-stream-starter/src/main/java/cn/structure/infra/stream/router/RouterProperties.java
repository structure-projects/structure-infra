package cn.structure.infra.stream.router;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "structure.infra.stream.router")
public class RouterProperties {

    private boolean enabled = true;
    private List<RouteDefinition> routes = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    public static class RouteDefinition {
        private String id;
        private String eventType;
        private String businessType;
        private String payloadType;
        private String condition;
        private String handlerBean;
        private String handlerMethod;
        private String description;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public String getPayloadType() {
            return payloadType;
        }

        public void setPayloadType(String payloadType) {
            this.payloadType = payloadType;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public String getHandlerBean() {
            return handlerBean;
        }

        public void setHandlerBean(String handlerBean) {
            this.handlerBean = handlerBean;
        }

        public String getHandlerMethod() {
            return handlerMethod;
        }

        public void setHandlerMethod(String handlerMethod) {
            this.handlerMethod = handlerMethod;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
