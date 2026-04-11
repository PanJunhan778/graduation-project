package com.pjh.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
