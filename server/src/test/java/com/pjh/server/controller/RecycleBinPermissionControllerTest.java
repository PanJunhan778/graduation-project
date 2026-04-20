package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RecycleBinPermissionControllerTest {

    @Test
    void financeRecycleBinEndpointsShouldRequireOwnerRole() throws NoSuchMethodException {
        assertControllerClassRole(FinanceController.class);
        assertOwnerOnlyMethod(FinanceController.class, "listRecycleBin", int.class, int.class);
        assertOwnerOnlyMethod(FinanceController.class, "restore", Long.class);
        assertOwnerOnlyMethod(FinanceController.class, "batchRestore", com.pjh.server.dto.BatchDeleteDTO.class);
    }

    @Test
    void employeeRecycleBinEndpointsShouldRequireOwnerRole() throws NoSuchMethodException {
        assertControllerClassRole(EmployeeController.class);
        assertOwnerOnlyMethod(EmployeeController.class, "listRecycleBin", int.class, int.class);
        assertOwnerOnlyMethod(EmployeeController.class, "restore", Long.class);
        assertOwnerOnlyMethod(EmployeeController.class, "batchRestore", com.pjh.server.dto.BatchDeleteDTO.class);
    }

    @Test
    void taxRecycleBinEndpointsShouldRequireOwnerRole() throws NoSuchMethodException {
        assertControllerClassRole(TaxController.class);
        assertOwnerOnlyMethod(TaxController.class, "listRecycleBin", int.class, int.class);
        assertOwnerOnlyMethod(TaxController.class, "restore", Long.class);
        assertOwnerOnlyMethod(TaxController.class, "batchRestore", com.pjh.server.dto.BatchDeleteDTO.class);
    }

    private void assertControllerClassRole(Class<?> controllerClass) {
        SaCheckRole annotation = controllerClass.getAnnotation(SaCheckRole.class);
        assertNotNull(annotation, () -> controllerClass.getSimpleName() + " should keep class-level role protection");
        assertArrayEquals(new String[]{"owner", "staff"}, annotation.value());
        assertEquals(SaMode.OR, annotation.mode());
    }

    private void assertOwnerOnlyMethod(Class<?> controllerClass, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        SaCheckRole annotation = method.getAnnotation(SaCheckRole.class);
        assertNotNull(annotation, () -> controllerClass.getSimpleName() + "." + methodName + " should require owner role");
        assertArrayEquals(new String[]{"owner"}, annotation.value());
    }
}
