package com.pjh.server.dashboard;

import com.pjh.server.util.CurrentSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HomeAiSummarySnapshotInvalidationPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final CurrentSessionService currentSessionService;

    public void publishCurrentCompany() {
        publish(currentSessionService.requireCurrentCompanyId());
    }

    public void publish(Long companyId) {
        if (companyId == null) {
            return;
        }
        eventPublisher.publishEvent(new HomeAiSummarySnapshotInvalidationEvent(companyId));
    }
}
