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