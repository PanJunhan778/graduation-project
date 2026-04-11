package com.pjh.server.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.audit.AuditUpdate;
import com.pjh.server.common.Result;
import com.pjh.server.dto.EmployeeUpsertDTO;
import com.pjh.server.entity.Employee;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.service.EmployeeService;
import com.pjh.server.vo.EmployeeVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] AUDIT_FIELDS = {"name", "department", "position", "salary", "hireDate", "status", "remark"};

    private final EmployeeMapper employeeMapper;
    private final AuditOperationService auditOperationService;

    @Override
    public IPage<EmployeeVO> listEmployees(int page, int size, String department, Integer status) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .eq(StrUtil.isNotBlank(department), Employee::getDepartment, department)
                .eq(status != null, Employee::getStatus, status)
                .orderByDesc(Employee::getHireDate)
                .orderByDesc(Employee::getId);

        IPage<Employee> employeePage = employeeMapper.selectPage(new Page<>(page, size), wrapper);
        return employeePage.convert(this::toVO);
    }

    @Override
    @Transactional
    public void createEmployee(EmployeeUpsertDTO dto) {
        Employee employee = new Employee();
        applyUpsert(employee, dto);
        employeeMapper.insert(employee);
        auditOperationService.publishCreate("employee", employee.getId(), employee, AUDIT_FIELDS);
    }

    @Override
    @Transactional
    @AuditUpdate(
            module = "employee",
            fields = {"name", "department", "position", "salary", "hireDate", "status", "remark"}
    )
    public void updateEmployee(Long id, EmployeeUpsertDTO dto) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException("员工记录不存在");
        }
        applyUpsert(employee, dto);
        employeeMapper.updateById(employee);
    }

    @Override
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException("员工记录不存在");
        }
        employeeMapper.deleteById(id);
        auditOperationService.publishDelete("employee", id, employee, AUDIT_FIELDS);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的数据");
        }
        List<Employee> employees = employeeMapper.selectBatchIds(ids);
        employeeMapper.deleteBatchIds(ids);
        employees.forEach(employee -> auditOperationService.publishDelete("employee", employee.getId(), employee, AUDIT_FIELDS));
    }

    @Override
    @Transactional
    public Result<?> importExcel(MultipartFile file) {
        return EmployeeImportExcelHelper.importExcel(file, employeeMapper);
    }

    @SuppressWarnings("unused")
    private Result<?> importExcelLegacy(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要导入的文件");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Employee> employees = new ArrayList<>();

        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            List<Map<String, Object>> rows = reader.readAll();

            if (rows.isEmpty()) {
                return Result.fail("文件中没有数据");
            }

            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                if (isBlankRow(row)) {
                    continue;
                }

                int rowNum = i + 2;
                String name = Convert.toStr(row.get("员工姓名"));
                String department = Convert.toStr(row.get("所属部门"));
                String position = Convert.toStr(row.get("职位"));
                Object salaryObj = row.get("基础薪资");
                Object hireDateObj = row.get("入职日期");
                Object statusObj = row.get("在职状态");
                String remark = Convert.toStr(row.get("备注"));

                if (StrUtil.isBlank(name)) {
                    errors.add(errorEntry(rowNum, "员工姓名不能为空"));
                    continue;
                }

                if (StrUtil.isBlank(department)) {
                    errors.add(errorEntry(rowNum, "所属部门不能为空"));
                    continue;
                }

                if (salaryObj == null || StrUtil.isBlank(Convert.toStr(salaryObj))) {
                    errors.add(errorEntry(rowNum, "基础薪资不能为空"));
                    continue;
                }

                BigDecimal salary;
                try {
                    salary = Convert.toBigDecimal(salaryObj);
                } catch (Exception e) {
                    errors.add(errorEntry(rowNum, "基础薪资格式不正确"));
                    continue;
                }
                if (salary == null || salary.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(errorEntry(rowNum, "基础薪资不能小于0"));
                    continue;
                }

                if (hireDateObj == null || StrUtil.isBlank(Convert.toStr(hireDateObj))) {
                    errors.add(errorEntry(rowNum, "入职日期不能为空"));
                    continue;
                }

                LocalDate hireDate;
                try {
                    hireDate = parseDate(hireDateObj);
                } catch (Exception e) {
                    errors.add(errorEntry(rowNum, "入职日期格式不正确，请使用 yyyy-MM-dd"));
                    continue;
                }

                Integer status;
                try {
                    status = parseStatus(statusObj);
                } catch (BusinessException e) {
                    errors.add(errorEntry(rowNum, e.getMessage()));
                    continue;
                }

                Employee employee = new Employee();
                employee.setName(name.trim());
                employee.setDepartment(department.trim());
                employee.setPosition(StrUtil.isBlank(position) ? null : position.trim());
                employee.setSalary(salary);
                employee.setHireDate(hireDate);
                employee.setStatus(status);
                employee.setRemark(StrUtil.isBlank(remark) ? null : remark.trim());
                employees.add(employee);
            }
        } catch (Exception e) {
            log.error("员工 Excel 解析失败", e);
            return Result.fail("文件解析失败，请检查文件格式是否为 .xlsx");
        }

        if (!errors.isEmpty()) {
            return Result.fail(400, "数据校验失败，全部未导入", errors);
        }

        for (Employee employee : employees) {
            employeeMapper.insert(employee);
        }

        return Result.success("成功导入 " + employees.size() + " 条员工记录", null);
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        EmployeeTemplateWorkbookHelper.downloadTemplate(response);
    }

    @SuppressWarnings("unused")
    private void downloadTemplateLegacy(HttpServletResponse response) {
        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            List<Map<String, Object>> sampleData = new ArrayList<>();
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("员工姓名", "张三");
            sample.put("所属部门", "市场部");
            sample.put("职位", "招商主管");
            sample.put("基础薪资", "8000.00");
            sample.put("入职日期", "2026-04-01");
            sample.put("在职状态", "在职");
            sample.put("备注", "示例数据，请删除此行");
            sampleData.add(sample);

            writer.write(sampleData, true);
            writer.setColumnWidth(0, 16);
            writer.setColumnWidth(1, 16);
            writer.setColumnWidth(2, 16);
            writer.setColumnWidth(3, 14);
            writer.setColumnWidth(4, 16);
            writer.setColumnWidth(5, 14);
            writer.setColumnWidth(6, 24);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = URLEncoder.encode("员工导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            ServletOutputStream out = response.getOutputStream();
            writer.flush(out);
            out.flush();
        } catch (Exception e) {
            log.error("员工导入模板下载失败", e);
            throw new BusinessException("模板下载失败");
        }
    }

    private void applyUpsert(Employee employee, EmployeeUpsertDTO dto) {
        employee.setName(dto.getName().trim());
        employee.setDepartment(dto.getDepartment().trim());
        employee.setPosition(StrUtil.isBlank(dto.getPosition()) ? null : dto.getPosition().trim());
        employee.setSalary(dto.getSalary());
        employee.setHireDate(dto.getHireDate());
        employee.setStatus(dto.getStatus());
        employee.setRemark(StrUtil.isBlank(dto.getRemark()) ? null : dto.getRemark().trim());
    }

    private EmployeeVO toVO(Employee employee) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setDepartment(employee.getDepartment());
        vo.setPosition(employee.getPosition());
        vo.setSalary(employee.getSalary());
        vo.setHireDate(employee.getHireDate());
        vo.setStatus(employee.getStatus());
        vo.setRemark(employee.getRemark());
        vo.setCreatedTime(employee.getCreatedTime());
        return vo;
    }

    private LocalDate parseDate(Object value) {
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(Convert.toStr(value).trim(), DATE_FMT);
    }

    private Integer parseStatus(Object value) {
        String raw = Convert.toStr(value);
        if (StrUtil.isBlank(raw)) {
            return 1;
        }

        String normalized = raw.trim();
        return switch (normalized) {
            case "在职", "1" -> 1;
            case "离职", "0" -> 0;
            default -> throw new BusinessException("在职状态必须为 在职/离职 或 1/0");
        };
    }

    private boolean isBlankRow(Map<String, Object> row) {
        for (Object value : row.values()) {
            if (value != null && StrUtil.isNotBlank(Convert.toStr(value))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> errorEntry(int row, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("row", row);
        entry.put("error", error);
        return entry;
    }
}
