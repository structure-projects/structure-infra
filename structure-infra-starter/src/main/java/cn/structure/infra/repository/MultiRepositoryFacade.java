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

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MultiRepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>> extends RepositoryFacade<T, ID, D> {

    protected final Map<RepositoryType, D> delegates = new HashMap<>();

    @Getter
    @Setter
    private RepositoryType defaultType = RepositoryType.AUTO;

    public void registerDelegate(RepositoryType type, D delegate) {
        delegates.put(type, delegate);
        log.info("Registered repository delegate: type={}, delegate={}", type, delegate.getClass().getSimpleName());
    }

    public D getCurrentDelegate() {
        RepositoryType contextType = RepositoryTypeContext.get();
        if (contextType != null && delegates.containsKey(contextType)) {
            log.debug("Using context repository type: {}", contextType);
            return delegates.get(contextType);
        }

        if (defaultType != RepositoryType.AUTO && delegates.containsKey(defaultType)) {
            log.debug("Using default repository type: {}", defaultType);
            return delegates.get(defaultType);
        }

        if (getDelegate() != null) {
            log.debug("Using default delegate from parent");
            return getDelegate();
        }

        if (!delegates.isEmpty()) {
            D firstDelegate = delegates.values().iterator().next();
            log.debug("Using first available delegate: {}", firstDelegate.getClass().getSimpleName());
            return firstDelegate;
        }

        throw new IllegalStateException("No repository delegate available");
    }

    public boolean hasDelegate(RepositoryType type) {
        return delegates.containsKey(type);
    }

    public Set<RepositoryType> getRegisteredTypes() {
        return Collections.unmodifiableSet(delegates.keySet());
    }

    @Override
    public T save(T entity) {
        return getCurrentDelegate().save(entity);
    }

    @Override
    public void removeById(ID id) {
        getCurrentDelegate().removeById(id);
    }

    @Override
    public T findById(ID id) {
        return getCurrentDelegate().findById(id);
    }

    @Override
    public T queryById(ID id) {
        return getCurrentDelegate().queryById(id);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        return getCurrentDelegate().queryByIdOptional(id);
    }

    @Override
    public T queryOne(T entity) {
        return getCurrentDelegate().queryOne(entity);
    }

    @Override
    public Optional<T> queryOneOptional(T entity) {
        return getCurrentDelegate().queryOneOptional(entity);
    }

    @Override
    public List<T> queryList(T entity) {
        return getCurrentDelegate().queryList(entity);
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        return getCurrentDelegate().queryPage(reqPage);
    }

    @Override
    public List<T> saveBatch(List<T> entities) {
        return getCurrentDelegate().saveBatch(entities);
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        getCurrentDelegate().removeBatchByIds(ids);
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        return getCurrentDelegate().listByIds(ids);
    }

    @Override
    public long count(T entity) {
        return getCurrentDelegate().count(entity);
    }

    @Override
    public boolean exists(T entity) {
        return getCurrentDelegate().exists(entity);
    }
}