package cn.structure.infra.repository;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;

@Slf4j
public class RepositoryTypeContext implements Closeable {

    private static final ThreadLocal<RepositoryType> context = new ThreadLocal<>();

    private RepositoryTypeContext(RepositoryType type) {
        context.set(type);
    }

    public static RepositoryType get() {
        return context.get();
    }

    public static void set(RepositoryType type) {
        context.set(type);
        log.debug("Set repository type context: {}", type);
    }

    public static void clear() {
        context.remove();
    }

    public static RepositoryTypeContext use(RepositoryType type) {
        return new RepositoryTypeContext(type);
    }

    @Override
    public void close() {
        clear();
        log.debug("Cleared repository type context");
    }
}