package com.pjh.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.audit.AuditOperationGroupKey;
import com.pjh.server.audit.AuditOperationGroupQuery;
import com.pjh.server.audit.AuditOperationGroupRow;
import com.pjh.server.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM (
                SELECT 1
                FROM audit_log
                WHERE company_id = #{query.companyId}
                  AND is_deleted = 0
                  <if test="query.module != null">
                    AND module = #{query.module}
                  </if>
                  <if test="query.operationType != null">
                    AND operation_type = #{query.operationType}
                  </if>
                  <if test="query.startDateTime != null">
                    AND operation_time <![CDATA[ >= ]]> #{query.startDateTime}
                  </if>
                  <if test="query.endExclusive != null">
                    AND operation_time <![CDATA[ < ]]> #{query.endExclusive}
                  </if>
                GROUP BY module, operation_type, target_id, user_id, operation_time
            ) grouped
            </script>
            """)
    long countOperationGroups(@Param("query") AuditOperationGroupQuery query);

    @Select("""
            <script>
            SELECT
                module,
                operation_type,
                target_id,
                user_id,
                operation_time,
                MAX(id) AS max_id,
                COUNT(*) AS change_count
            FROM audit_log
            WHERE company_id = #{query.companyId}
              AND is_deleted = 0
              <if test="query.module != null">
                AND module = #{query.module}
              </if>
              <if test="query.operationType != null">
                AND operation_type = #{query.operationType}
              </if>
              <if test="query.startDateTime != null">
                AND operation_time <![CDATA[ >= ]]> #{query.startDateTime}
              </if>
              <if test="query.endExclusive != null">
                AND operation_time <![CDATA[ < ]]> #{query.endExclusive}
              </if>
            GROUP BY module, operation_type, target_id, user_id, operation_time
            ORDER BY operation_time DESC, max_id DESC
            LIMIT #{query.offset}, #{query.size}
            </script>
            """)
    List<AuditOperationGroupRow> selectOperationGroups(@Param("query") AuditOperationGroupQuery query);

    @Select("""
            <script>
            SELECT
                id,
                company_id,
                user_id,
                module,
                operation_type,
                target_id,
                field_name,
                old_value,
                new_value,
                operation_time,
                remark,
                is_deleted
            FROM audit_log
            WHERE company_id = #{companyId}
              AND is_deleted = 0
              <if test="keys != null and keys.size() > 0">
                AND
                <foreach collection="keys" item="key" separator=" OR " open="(" close=")">
                    (
                        module = #{key.module}
                        AND operation_type = #{key.operationType}
                        AND target_id = #{key.targetId}
                        AND user_id = #{key.userId}
                        AND operation_time = #{key.operationTime}
                    )
                </foreach>
              </if>
            ORDER BY operation_time DESC, id ASC
            </script>
            """)
    List<AuditLog> selectByOperationGroups(
            @Param("companyId") Long companyId,
            @Param("keys") List<AuditOperationGroupKey> keys
    );
}
