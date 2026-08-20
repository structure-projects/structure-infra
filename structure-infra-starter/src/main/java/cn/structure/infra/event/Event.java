/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.event;

/**
 * <p>
 * 事件接口
 * </p>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/1 17:01
 */
public interface Event {

    /**
     * 获取事件ID 事件ID
     *
     * @return 事件ID
     */
    String getEventId();

    /**
     * 获取事件渠道类型
     *
     * @return 事件类型
     */
    default EventChannel getEventChannel() {
        return EventChannel.DEFAULT;
    }

}
