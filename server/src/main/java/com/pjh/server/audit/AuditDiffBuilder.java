package com.pjh.server.audit;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AuditDiffBuilder {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<AuditFieldChange> buildChanges(Object before, Object after, String[] fields) {
        if (before == null || after == null || fields == null || fields.length == 0) {
            return List.of();
        }
        List<AuditFieldChange> changes = new ArrayList<>();

        for (String field : fields) {
            Object oldValue = readFieldValue(before, field);
            Object newValue = readFieldValue(after, field);

            if (!isSameValue(oldValue, newValue)) {
                changes.add(new AuditFieldChange(field, formatValue(oldValue), formatValue(newValue)));
            }
        }

        return changes;
    }

    public List<AuditFieldChange> buildCreateChanges(Object current, String[] fields) {
        return buildSnapshotChanges(current, fields, true);
    }

    public List<AuditFieldChange> buildDeleteChanges(Object current, String[] fields) {
        return buildSnapshotChanges(current, fields, false);
    }

    public Map<String, String> snapshot(Object source, String[] fields) {
        if (source == null || fields == null || fields.length == 0) {
            return Map.of();
        }

        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String field : fields) {
            snapshot.put(field, formatValue(readFieldValue(source, field)));
        }
        return snapshot;
    }

    private List<AuditFieldChange> buildSnapshotChanges(Object snapshot, String[] fields, boolean createOperation) {
        if (snapshot == null || fields == null || fields.length == 0) {
            return List.of();
        }
        List<AuditFieldChange> changes = new ArrayList<>();

        for (String field : fields) {
            Object value = readFieldValue(snapshot, field);
            if (value == null) {
                continue;
            }

            String formattedValue = formatValue(value);
            changes.add(createOperation
                    ? new AuditFieldChange(field, null, formattedValue)
                    : new AuditFieldChange(field, formattedValue, null));
        }

        return changes;
    }

    private Object readFieldValue(Object source, String field) {
        if (source instanceof Map<?, ?> map) {
            return map.get(field);
        }
        BeanWrapper wrapper = new BeanWrapperImpl(source);
        return wrapper.getPropertyValue(field);
    }

    private boolean isSameValue(Object oldValue, Object newValue) {
        if (oldValue instanceof BigDecimal oldAmount && newValue instanceof BigDecimal newAmount) {
            return oldAmount.compareTo(newAmount) == 0;
        }
        return Objects.equals(oldValue, newValue);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal amount) {
            return amount.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FMT);
        }
        return String.valueOf(value);
    }
}
