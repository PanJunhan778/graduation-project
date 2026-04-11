package com.pjh.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
