package io.emop.example.service.api.hello;

import io.emop.example.model.hello.HelloTask;
import io.emop.model.annotation.Remote;

import java.util.List;

/**
 * Hello任务服务接口
 * 支持RPC远程调用
 */
@Remote
public interface HelloTaskService {
    
    /**
     * 样例 service
     * @param content 内容
     * @return 结果
     */
    String sayHello(String content);
}