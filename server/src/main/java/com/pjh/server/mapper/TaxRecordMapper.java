package com.pjh.server.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.TaxRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TaxRecordMapper extends BaseMapper<TaxRecord> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(SUM(tax_amount), 0)
            FROM tax_record
            WHERE company_id = #{companyId}
              AND is_deleted = 0
              AND payment_status = 0
              AND tax_amount > 0
            """)
    BigDecimal selectUnpaidTaxTotalByCompanyId(@Param("companyId") Long companyId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, company_id, tax_period, tax_type, declaration_type, tax_amount,
                   payment_status, payment_date, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM tax_record
            WHERE company_id = #{companyId}
              AND is_deleted = 0
            ORDER BY id ASC
            """)
    List<TaxRecord> selectHomeTaxCalendarRecordsByCompanyId(@Param("companyId") Long companyId);

    @Select("""
            SELECT id, company_id, tax_period, tax_type, declaration_type, tax_amount,
                   payment_status, payment_date, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM tax_record
            WHERE company_id = #{companyId}
              AND is_deleted = 1
            ORDER BY updated_time DESC, id DESC
            """)
    List<TaxRecord> selectDeletedByCompanyId(@Param("companyId") Long companyId);

    @Select("""
            SELECT id, company_id, tax_period, tax_type, declaration_type, tax_amount,
                   payment_status, payment_date, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM tax_record
            WHERE company_id = #{companyId}
              AND id = #{id}
              AND is_deleted = 1
            """)
    TaxRecord selectDeletedById(@Param("companyId") Long companyId, @Param("id") Long id);

    @Select("""
            <script>
            SELECT id, company_id, tax_period, tax_type, declaration_type, tax_amount,
                   payment_status, payment_date, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM tax_record
            WHERE company_id = #{companyId}
              AND is_deleted = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY updated_time DESC, id DESC
            </script>
            """)
    List<TaxRecord> selectDeletedByIds(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);

    @Update("""
            UPDATE tax_record
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
            UPDATE tax_record
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
