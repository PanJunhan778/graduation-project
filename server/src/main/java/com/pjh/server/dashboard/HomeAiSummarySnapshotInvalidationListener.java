package com.pjh.server.dashboard;

import com.pjh.server.service.HomeAiSummarySnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomeAiSummarySnapshotInvalidationListener {

    private final HomeAiSummarySnapshotService homeAiSummarySnapshotService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(HomeAiSummarySnapshotInvalidationEvent event) {
        try {
            homeAiSummarySnapshotService.markDirty(event.companyId());
        } catch (Exception exception) {
            log.warn("Failed to mark home AI summary snapshot dirty, companyId={}", event.companyId(), exception);
        }
    }
}
