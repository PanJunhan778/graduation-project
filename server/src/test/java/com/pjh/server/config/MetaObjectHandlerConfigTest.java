package com.pjh.server.config;

import com.pjh.server.entity.FinanceRecord;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaObjectHandlerConfigTest {

    private final MetaObjectHandlerConfig handler = new MetaObjectHandlerConfig();

    @Test
    void updateFillShouldOverwriteExistingUpdatedTime() {
        FinanceRecord record = new FinanceRecord();
        LocalDateTime originalTime = LocalDateTime.of(2026, 4, 11, 11, 0, 0);
        record.setUpdatedTime(originalTime);
        MetaObject metaObject = SystemMetaObject.forObject(record);

        handler.updateFill(metaObject);

        assertTrue(record.getUpdatedTime().isAfter(originalTime));
    }
}
