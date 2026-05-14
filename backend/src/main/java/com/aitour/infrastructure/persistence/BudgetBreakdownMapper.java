/*
 * @author myoung
 */
package com.aitour.infrastructure.persistence;

import com.aitour.domain.BudgetBreakdown;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BudgetBreakdownMapper extends BaseMapper<BudgetBreakdown> {
}
