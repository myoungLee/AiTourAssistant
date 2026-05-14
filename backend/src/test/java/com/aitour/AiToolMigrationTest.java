/*
 * @author myoung
 */
package com.aitour;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AI 调用和工具调用日志表可以由 Flyway 创建。
 *
 * @author myoung
 */
@SpringBootTest
class AiToolMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateAiAndToolLogTables() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('llm_call_log', 'tool_call_log')
                """, Integer.class);

        assertThat(count).isEqualTo(2);
    }
}
