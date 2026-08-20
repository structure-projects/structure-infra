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

import cn.structure.common.repository.ICrudRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

@Getter
    @Setter
    @Slf4j
    public class RepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>> implements ICrudRepository<T, ID> {

    @Autowired
    protected D delegate;

    protected Class<T> entityClass;

    @Override
    public T save(T entity) {
        return delegate.save(entity);
    }

    @Override
    public void removeById(ID id) {
        delegate.removeById(id);
    }

    @Override
    public T findById(ID id) {
        return delegate.findById(id);
    }

    @Override
    public T queryById(ID id) {
        return delegate.queryById(id);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        T entity = delegate.queryById(id);
        return Optional.ofNullable(entity);
    }

    @Override
    public T queryOne(T entity) {
        return delegate.queryOne(entity);
    }

    @Override
    public Optional<T> queryOneOptional(T entity) {
        return delegate.queryOneOptional(entity);
    }

    @Override
    public List<T> queryList(T entity) {
        List<T> entityList = delegate.queryList(entity);
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        return delegate.queryPage(reqPage);
    }

    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return delegate.saveBatch(entities);
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        delegate.removeBatchByIds(ids);
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        List<T> entityList = delegate.listByIds(ids);
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    @Override
    public long count(T entity) {
        return delegate.count(entity);
    }

    @Override
    public boolean exists(T entity) {
        return delegate.exists(entity);
    }
}
