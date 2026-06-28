package cn.structure.infra.sample.mongodb.controller;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PostMapping
    public UserEntity createUser(@RequestBody UserEntity user) {
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    @GetMapping("/{id}")
    public UserEntity getUserById(@PathVariable("id") Long id) {
        return userRepository.findById(id);
    }

    @GetMapping("/name/{username}")
    public UserEntity getUserByName(@PathVariable("username") String username) {
        return userRepository.findByName(username);
    }

    @GetMapping("/list")
    public List<UserEntity> listUsers() {
        return userRepository.queryList(null);
    }

    @GetMapping("/page")
    public ResPage<UserEntity> pageUsers(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        ReqPage reqPage = new ReqPage();
        reqPage.setPage(page);
        reqPage.setSize(size);
        return userRepository.queryPage(reqPage);
    }

    @PutMapping("/{id}")
    public UserEntity updateUser(@PathVariable("id") Long id, @RequestBody UserEntity user) {
        UserEntity exist = userRepository.findById(id);
        if (exist == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setId(id);
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id) {
        userRepository.removeById(id);
        return "删除成功";
    }

    @PostMapping("/batch")
    public List<UserEntity> batchCreate(@RequestBody List<UserEntity> users) {
        LocalDateTime now = LocalDateTime.now();
        users.forEach(u -> {
            u.setCreateTime(now);
            u.setUpdateTime(now);
        });
        return userRepository.saveBatch(users);
    }

    @GetMapping("/count")
    public long countUsers() {
        return userRepository.count(null);
    }
}
