package cn.structure.infra.event;

import cn.structure.infra.properties.InfraProperties;
import cn.structured.datascope.message.wrapper.DataScopeStreamBridge;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * <p>
 * 事件管理器
 * </p>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/21 16:05
 */
@AllArgsConstructor
public class DefaultEventManagerImpl implements EventManager {

    private final ApplicationEventPublisher eventPublisher;

    private final DataScopeStreamBridge streamBridge;

    private final InfraProperties infraProperties;

    @Override
    public void publish(Event event) {
        if (event.getEventChannel().equals(EventChannel.DEFAULT)) {
            if (infraProperties.getDefaultEventChannel() == EventChannel.SPRING_EVENT) {
                eventPublisher.publishEvent(event);
            }
            if (infraProperties.getDefaultEventChannel() == EventChannel.MESSAGE_EVENT) {
                streamBridge.send(event.getEventId(), event);
            }
        } else {
            if (event.getEventChannel() == EventChannel.SPRING_EVENT) {
                eventPublisher.publishEvent(event);
            }
            if (event.getEventChannel() == EventChannel.MESSAGE_EVENT) {
                streamBridge.send(event.getEventId(), event);
            }
        }
    }
}
