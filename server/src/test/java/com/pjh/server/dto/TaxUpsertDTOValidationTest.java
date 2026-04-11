package com.pjh.server.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxUpsertDTOValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void tearDown() {
        FACTORY.close();
    }

    @Test
    void taxPeriodShouldAcceptMonthlyQuarterlyAndAnnualFormats() {
        for (String taxPeriod : new String[]{"2026-03", "2026-Q2", "2026-Annual"}) {
            TaxUpsertDTO dto = createValidDto();
            dto.setTaxPeriod(taxPeriod);

            assertTrue(VALIDATOR.validate(dto).isEmpty(), "expected valid taxPeriod: " + taxPeriod);
        }
    }

    @Test
    void taxPeriodShouldRejectInvalidFormats() {
        for (String taxPeriod : new String[]{"2026-13", "2026-Q5"}) {
            TaxUpsertDTO dto = createValidDto();
            dto.setTaxPeriod(taxPeriod);

            Set<?> violations = VALIDATOR.validate(dto);
            assertFalse(violations.isEmpty(), "expected invalid taxPeriod: " + taxPeriod);
        }
    }

    private TaxUpsertDTO createValidDto() {
        TaxUpsertDTO dto = new TaxUpsertDTO();
        dto.setTaxPeriod("2026-03");
        dto.setTaxType("增值税");
        dto.setTaxAmount(BigDecimal.ZERO);
        dto.setPaymentStatus(0);
        return dto;
    }
}
