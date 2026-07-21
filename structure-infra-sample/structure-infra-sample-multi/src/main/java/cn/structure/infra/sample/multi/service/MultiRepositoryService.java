package cn.structure.infra.sample.multi.service;

import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.repository.RepositoryTypeContext;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.domain.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class MultiRepositoryService {

    private final UserRepository userRepository;

    public UserEntity saveToMybatis(UserEntity user) {
        log.info("Saving user to MyBatis Plus repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            UserEntity saved = userRepository.save(user);
            log.info("User saved to MyBatis Plus with id: {}", saved.getId());
            return saved;
        }
    }

    public UserEntity saveToMongo(UserEntity user) {
        log.info("Saving user to MongoDB repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            UserEntity saved = userRepository.save(user);
            log.info("User saved to MongoDB with id: {}", saved.getId());
            return saved;
        }
    }

    public UserEntity saveToDefault(UserEntity user) {
        log.info("Saving user to default repository (configured in application.yml)");
        UserEntity saved = userRepository.save(user);
        log.info("User saved to default repository with id: {}", saved.getId());
        return saved;
    }

    public UserEntity findFromMybatis(Long id) {
        log.info("Finding user from MyBatis Plus repository, id: {}", id);
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            return userRepository.queryById(id);
        }
    }

    public UserEntity findFromMongo(Long id) {
        log.info("Finding user from MongoDB repository, id: {}", id);
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            return userRepository.queryById(id);
        }
    }

    public List<UserEntity> queryListFromMybatis(UserEntity condition) {
        log.info("Querying user list from MyBatis Plus repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            return userRepository.queryList(condition);
        }
    }

    public List<UserEntity> queryListFromMongo(UserEntity condition) {
        log.info("Querying user list from MongoDB repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            return userRepository.queryList(condition);
        }
    }

    public void deleteFromMybatis(Long id) {
        log.info("Deleting user from MyBatis Plus repository, id: {}", id);
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            userRepository.removeById(id);
        }
    }

    public void deleteFromMongo(Long id) {
        log.info("Deleting user from MongoDB repository, id: {}", id);
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            userRepository.removeById(id);
        }
    }

    public UserEntity findByNameFromMybatis(String name) {
        log.info("Finding user by name from MyBatis Plus repository, name: {}", name);
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            return userRepository.findByName(name);
        }
    }

    public UserEntity findByNameFromMongo(String name) {
        log.info("Finding user by name from MongoDB repository, name: {}", name);
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            return userRepository.findByName(name);
        }
    }

    public long countInMybatis(UserEntity condition) {
        log.info("Counting users in MyBatis Plus repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            return userRepository.count(condition);
        }
    }

    public long countInMongo(UserEntity condition) {
        log.info("Counting users in MongoDB repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            return userRepository.count(condition);
        }
    }

    public boolean existsInMybatis(UserEntity condition) {
        log.info("Checking user existence in MyBatis Plus repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MYBATIS_PLUS)) {
            return userRepository.exists(condition);
        }
    }

    public boolean existsInMongo(UserEntity condition) {
        log.info("Checking user existence in MongoDB repository");
        try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
            return userRepository.exists(condition);
        }
    }
}