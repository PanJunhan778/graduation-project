package com.pjh.server.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.FinanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FinanceRecordMapper extends BaseMapper<FinanceRecord> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT type, COALESCE(SUM(amount), 0) AS total
            FROM finance_record
            WHERE company_id = #{companyId}
              AND is_deleted = 0
              AND date >= #{startDate}
              AND date <= #{endDate}
            GROUP BY type
            """)
    List<Map<String, Object>> selectCurrentMonthSummaryByCompanyId(@Param("companyId") Long companyId,
                                                                   @Param("startDate") LocalDate startDate,
                                                                   @Param("endDate") LocalDate endDate);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DATE_FORMAT(date, '%Y-%m') AS month,
                   COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income,
                   COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense
            FROM finance_record
            WHERE company_id = #{companyId}
              AND is_deleted = 0
              AND date >= #{startDate}
              AND date <= #{endDate}
            GROUP BY DATE_FORMAT(date, '%Y-%m')
            """)
    List<Map<String, Object>> selectHomeMonthlyTrendByCompanyId(@Param("companyId") Long companyId,
                                                                @Param("startDate") LocalDate startDate,
                                                                @Param("endDate") LocalDate endDate);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(1)
            FROM finance_record
            WHERE company_id = #{companyId}
              AND is_deleted = 0
            """)
    Long selectCountByCompanyId(@Param("companyId") Long companyId);

    @Select("""
            SELECT id, company_id, type, amount, category, project, date, remark,
                   created_by, created_time, updated_by, updated_time, is_deleted
            FROM finance_record
            WHERE company_id = #{companyId}
              AND is_deleted = 1
            ORDER BY updated_time DESC, id DESC
            """)
    List<FinanceRecord> selectDeletedByCompanyId(@Param("companyId") Long companyId);

    @Select("""
            SELECT id, company_id, type, amount, category, project, date, remark,
                   created_by, created_time, updated_by, updated_time, is_deleted
            FROM finance_record
            WHERE company_id = #{companyId}
              AND id = #{id}
              AND is_deleted = 1
            """)
    FinanceRecord selectDeletedById(@Param("companyId") Long companyId, @Param("id") Long id);

    @Select("""
            <script>
            SELECT id, company_id, type, amount, category, project, date, remark,
                   created_by, created_time, updated_by, updated_time, is_deleted
            FROM finance_record
            WHERE company_id = #{companyId}
              AND is_deleted = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY updated_time DESC, id DESC
            </script>
            """)
    List<FinanceRecord> selectDeletedByIds(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);

    @Update("""
            UPDATE finance_record
            SET is_deleted = 0,
                updated_by = #{updatedBy},
                updated_time = NOW()
            WHERE company_id = #{companyId}
              AND id = #{id}
              AND is_deleted = 1
            """)
    int restoreDeletedById(
            @Param("companyId") Long companyId,
            @Param("id") Long id,
            @Param("updatedBy") Long updatedBy
    );

    @Update("""
            <script>
            UPDATE finance_record
            SET is_deleted = 0,
                updated_by = #{updatedBy},
                updated_time = NOW()
            WHERE company_id = #{companyId}
              AND is_deleted = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int restoreDeletedBatch(
            @Param("companyId") Long companyId,
            @Param("ids") List<Long> ids,
            @Param("updatedBy") Long updatedBy
    );
}
