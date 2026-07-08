package cn.structure.infra.sample.stream.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private String paymentId;

    private String orderId;

    private String paymentStatus;

    private Double amount;

}
