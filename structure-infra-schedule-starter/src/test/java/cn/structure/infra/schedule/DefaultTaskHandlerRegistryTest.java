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