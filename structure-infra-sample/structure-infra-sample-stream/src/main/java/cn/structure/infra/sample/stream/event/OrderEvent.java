package cn.structure.infra.sample.stream.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private String orderId;

    private String orderNo;

    private String status;

    private Double amount;

}