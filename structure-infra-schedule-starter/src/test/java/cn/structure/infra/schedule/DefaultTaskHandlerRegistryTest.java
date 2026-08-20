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

package cn.structure.infra.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTaskHandlerRegistryTest {

    private DefaultTaskHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultTaskHandlerRegistry();
    }

    @Test
    void testRegisterAndGet() {
        String handlerName = "test-handler";
        TaskHandler handler = param -> {};

        registry.register(handlerName, handler);

        assertTrue(registry.contains(handlerName));
        assertNotNull(registry.get(handlerName));
        assertSame(handler, registry.get(handlerName));
    }

    @Test
    void testRegisterWithNullHandlerName() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(null, param -> {}));
    }

    @Test
    void testRegisterWithNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("test-handler", null));
    }

    @Test
    void testUnregister() {
        String handlerName = "test-handler";
        TaskHandler handler = param -> {};

        registry.register(handlerName, handler);
        assertTrue(registry.contains(handlerName));

        registry.unregister(handlerName);
        assertFalse(registry.contains(handlerName));
        assertNull(registry.get(handlerName));
    }

    @Test
    void testUnregisterNonExistent() {
        assertDoesNotThrow(() -> registry.unregister("non-existent"));
    }

    @Test
    void testGetNonExistent() {
        assertNull(registry.get("non-existent"));
    }

    @Test
    void testContainsNonExistent() {
        assertFalse(registry.contains("non-existent"));
    }

    @Test
    void testRegisterOverwrite() {
        String handlerName = "test-handler";
        TaskHandler handler1 = param -> {};
        TaskHandler handler2 = param -> {};

        registry.register(handlerName, handler1);
        assertSame(handler1, registry.get(handlerName));

        registry.register(handlerName, handler2);
        assertSame(handler2, registry.get(handlerName));
    }
}