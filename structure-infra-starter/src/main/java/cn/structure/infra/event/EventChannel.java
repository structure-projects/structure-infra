package cn.structure.infra.event;

import lombok.Getter;

@Getter
public enum EventChannel {
    DEFAULT,
    SPRING_EVENT,
    MESSAGE_EVENT,
    ;
}
