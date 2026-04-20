package com.pjh.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.exception.GlobalExceptionHandler;
import com.pjh.server.service.EmployeeService;
import com.pjh.server.vo.EmployeeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EmployeeController(employeeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShouldFallbackToDepartmentWhenKeywordIsBlank() throws Exception {
        Page<EmployeeVO> page = new Page<>(1, 20);
        when(employeeService.listEmployees(1, 20, "销售部", null)).thenReturn(page);

        mockMvc.perform(get("/api/employee/list")
                        .param("department", "销售部"))
                .andExpect(status().isOk());

        verify(employeeService).listEmployees(1, 20, "销售部", null);
    }
}
