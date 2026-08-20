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

package cn.structure.infra.sample.elasticsearch.controller;

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
    public ResPage<UserEntity> pageUsers(@RequestParam(name = "page", defaultValue = "1") int page,
                                         @RequestParam(name = "size", defaultValue = "10") int size) {
        ReqPage reqPage = new ReqPage();
        reqPage.setPage(page);
        reqPage.setSize(size);
        return userRepository.queryPage(reqPage);
    }

    @PutMapping("/{id}")
    public UserEntity updateUser(@PathVariable Long id, @RequestBody UserEntity user) {
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
