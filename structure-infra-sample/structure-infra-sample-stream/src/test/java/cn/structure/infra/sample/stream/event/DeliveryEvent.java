package cn.structure.infra.sample.stream.event;

public class DeliveryEvent {

    private String deliveryId;
    private String orderId;
    private String status;
    private String address;

    public DeliveryEvent() {
    }

    public DeliveryEvent(String deliveryId, String orderId, String status, String address) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.status = status;
        this.address = address;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String deliveryId;
        private String orderId;
        private String status;
        private String address;

        public Builder deliveryId(String deliveryId) {
            this.deliveryId = deliveryId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public DeliveryEvent build() {
            return new DeliveryEvent(deliveryId, orderId, status, address);
        }
    }
}
