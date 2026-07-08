package cn.structure.infra.sample.stream.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryEvent {

    private String deliveryId;

    private String orderId;

    private String status;

    private String address;

}