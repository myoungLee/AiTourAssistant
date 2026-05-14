/*
 * @author myoung
 */
package com.aitour.infrastructure.persistence;

import com.aitour.domain.TripDay;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TripDayMapper extends BaseMapper<TripDay> {
}
