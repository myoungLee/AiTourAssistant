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
 * 验证 Flyway 能创建行程持久化相关表。
 *
 * @author myoung
 */
@SpringBootTest
class TripMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateTripTables() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('trip_request', 'trip_plan', 'trip_day', 'trip_item', 'budget_breakdown')
                """, Integer.class);

        assertThat(count).isEqualTo(5);
    }
}
