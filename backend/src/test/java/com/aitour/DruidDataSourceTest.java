/*
 * @author myoung
 */
package com.aitour;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Spring Boot 启动后实际使用 Druid 连接池，避免配置回退到默认数据源。
 *
 * @author myoung
 */
@SpringBootTest
class DruidDataSourceTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldUseDruidDataSource() {
        assertThat(dataSource).isInstanceOf(DruidDataSource.class);
    }
}
