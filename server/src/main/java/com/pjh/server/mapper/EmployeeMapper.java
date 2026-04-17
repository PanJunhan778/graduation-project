package com.pjh.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pjh.server.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    @Select("""
            SELECT id, company_id, name, department, position, salary, hire_date,
                   user_id, status, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM employee
            WHERE company_id = #{companyId}
              AND is_deleted = 1
            ORDER BY updated_time DESC, id DESC
            """)
    List<Employee> selectDeletedByCompanyId(@Param("companyId") Long companyId);

    @Select("""
            SELECT id, company_id, name, department, position, salary, hire_date,
                   user_id, status, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM employee
            WHERE company_id = #{companyId}
              AND id = #{id}
              AND is_deleted = 1
            """)
    Employee selectDeletedById(@Param("companyId") Long companyId, @Param("id") Long id);

    @Select("""
            <script>
            SELECT id, company_id, name, department, position, salary, hire_date,
                   user_id, status, remark, created_by, created_time,
                   updated_by, updated_time, is_deleted
            FROM employee
            WHERE company_id = #{companyId}
              AND is_deleted = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY updated_time DESC, id DESC
            </script>
            """)
    List<Employee> selectDeletedByIds(@Param("companyId") Long companyId, @Param("ids") List<Long> ids);

    @Update("""
            UPDATE employee
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
            UPDATE employee
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
