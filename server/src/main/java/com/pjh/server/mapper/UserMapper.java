package com.pjh.server.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(1)
            FROM user
            WHERE company_id = #{companyId}
              AND role = #{role}
              AND is_deleted = 0
            """)
    Long selectRoleCountByCompanyId(@Param("companyId") Long companyId, @Param("role") String role);
}
