package com.cleardesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 启动上下文加载测试：会拉起 Spring 容器，依赖测试配置（排除 Redis，仍需本地 MySQL）。
 */
@SpringBootTest
class ClearDeskApplicationTests {

    @Test
    void contextLoads() {
    }
}
