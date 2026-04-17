package com.pjh.server.service;

import com.pjh.server.dashboard.HomeAiSummaryRefreshRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomeAiSummaryRefreshWorker {

    private final HomeAiSummarySnapshotService homeAiSummarySnapshotService;

    @Async("homeAiSummaryTaskExecutor")
    @EventListener
    public void handle(HomeAiSummaryRefreshRequestedEvent event) {
        try {
            homeAiSummarySnapshotService.refreshSummary(event.companyId());
        } catch (Exception exception) {
            log.error("Failed to refresh home AI summary asynchronously, companyId={}", event.companyId(), exception);
        }
    }
}
