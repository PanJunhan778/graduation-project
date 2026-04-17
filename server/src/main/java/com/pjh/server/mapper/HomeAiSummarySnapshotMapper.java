package com.pjh.server.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.HomeAiSummarySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HomeAiSummarySnapshotMapper extends BaseMapper<HomeAiSummarySnapshot> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, company_id, summary_lines_json, status, is_dirty, generated_at, refresh_started_at,
                   last_error, created_time, updated_time, is_deleted
            FROM home_ai_summary_snapshot
            WHERE company_id = #{companyId} AND is_deleted = 0
            LIMIT 1
            """)
    HomeAiSummarySnapshot selectByCompanyId(@Param("companyId") Long companyId);
}
