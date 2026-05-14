/*
 * @author myoung
 */
package com.aitour.mapper;

import com.aitour.common.entity.UserProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
