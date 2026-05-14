/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.client.ai.AiChatClient;
import com.aitour.common.dto.TripDtos;
import com.aitour.common.entity.TripDay;
import com.aitour.common.entity.TripPlan;
import com.aitour.domain.TripPlanStatus;
import com.aitour.mapper.TripDayMapper;
import com.aitour.mapper.TripPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * 验证流式规划服务可以创建并持久化生成完成的行程。
 *
 * @author myoung
 */
@SpringBootTest
class TripPlanningServiceTest {

    @Autowired
    private TripPlanningService tripPlanningService;

    @MockitoBean
    private AiChatClient aiChatClient;

    @Autowired
    private TripPlanMapper tripPlanMapper;

    @Autowired
    private TripDayMapper tripDayMapper;

    /**
     * 给定完整出行需求后，应生成 GENERATED 状态的行程并保存每日安排。
     */
    @Test
    void shouldPersistGeneratedPlanWhenStreamingCompletes() {
        mockAiStreamResponse();
        TripDtos.CreateTripRequest request = new TripDtos.CreateTripRequest(
                "成都",
                LocalDate.of(2099, 6, 1),
                2,
                BigDecimal.valueOf(3000),
                2,
                List.of("美食", "轻松"),
                "想吃火锅，不想太赶"
        );

        tripPlanningService.streamPlan(10001L, request, new SseEmitter(1000L));

        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getUserId, 10001L)
                .orderByDesc(TripPlan::getCreatedAt)
                .last("limit 1"));
        assertThat(plan).isNotNull();
        assertThat(plan.getStatus()).isEqualTo(TripPlanStatus.GENERATED.name());
        assertThat(tripDayMapper.selectCount(new LambdaQueryWrapper<TripDay>().eq(TripDay::getPlanId, plan.getId())).intValue())
                .isEqualTo(2);
    }

    /**
     * 使用测试替身隔离真实 Spring AI 模型调用，避免单元测试访问外部中转服务。
     */
    private void mockAiStreamResponse() {
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("测试 AI 摘要");
            return null;
        }).when(aiChatClient).streamChat(any(), any());
    }
}
