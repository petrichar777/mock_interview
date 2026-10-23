package com.interviewmaster.backend.event;

import com.interviewmaster.backend.service.InterviewRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomReportEventListener {

    private final InterviewRoomService interviewRoomService;

    @Async("roomReportExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomReportRequested(RoomReportRequestedEvent event) {
        try {
            interviewRoomService.generateReport(event.roomId());
        } catch (Exception ex) {
            log.error("room report generation failed, roomId={}", event.roomId(), ex);
        }
    }
}
