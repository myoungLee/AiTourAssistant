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
 * 验证 Flyway 基础迁移可以从空库创建用户相关表。
 *
 * @author myoung
 */
@SpringBootTest
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateUsersTable() {
        Integer count = countPublicTable("users");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldCreateUserProfileTable() {
        Integer count = countPublicTable("user_profile");

        assertThat(count).isEqualTo(1);
    }

    private Integer countPublicTable(String tableName) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                Integer.class,
                tableName
        );
    }
}
