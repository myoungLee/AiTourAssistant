/*
 * @author myoung
 */
package com.aitour.client.mcp.local;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 本地预算估算占位工具，按天数和人数给出粗略费用拆分。
 *
 * @author myoung
 */
@Component
public class LocalBudgetTool implements TravelTool {
    @Override
    public String name() {
        return "budget.estimate";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        int days = numberArgument(request, "days", 3);
        int people = numberArgument(request, "peopleCount", 2);
        BigDecimal hotel = BigDecimal.valueOf(350L * Math.max(days - 1, 1) * people);
        BigDecimal food = BigDecimal.valueOf(160L * days * people);
        BigDecimal transport = BigDecimal.valueOf(80L * days * people);
        BigDecimal ticket = BigDecimal.valueOf(120L * days * people);
        BigDecimal other = BigDecimal.valueOf(60L * days * people);
        BigDecimal total = hotel.add(food).add(transport).add(ticket).add(other);

        return new ToolResult(name(), true, "预计总预算约 " + total + " 元。", Map.of(
                "hotel", hotel,
                "food", food,
                "transport", transport,
                "ticket", ticket,
                "other", other,
                "total", total
        ));
    }

    private int numberArgument(ToolRequest request, String key, int fallback) {
        Object value = request.arguments().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }
}
