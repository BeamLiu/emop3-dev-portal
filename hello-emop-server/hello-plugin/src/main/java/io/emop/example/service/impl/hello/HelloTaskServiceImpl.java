package io.emop.example.service.impl.hello;

import io.emop.example.service.api.hello.HelloTaskService;
import io.emop.model.annotation.Service;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Hello任务服务实现
 */
@Slf4j
@Service
public class HelloTaskServiceImpl implements HelloTaskService {

    @Override
    public String sayHello(@NonNull String content) {
        return "Hello " + content;
    }
}