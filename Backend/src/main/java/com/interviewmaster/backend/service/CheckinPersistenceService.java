package com.interviewmaster.backend.service;

import com.interviewmaster.backend.mapper.CheckinDayMapper;
import com.interviewmaster.backend.model.entity.CheckinDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinPersistenceService {

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = { 1000, 3000, 9000 };

    private final CheckinDayMapper checkinDayMapper;

    @Async("checkinPersistenceExecutor")
    public void persistCheckinAsync(CheckinDay checkinDay) {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                checkinDayMapper.insert(checkinDay);
                return;
            } catch (DataIntegrityViolationException ex) {
                log.warn("Duplicate checkin ignored asynchronously, userId={}, date={}",
                        checkinDay.getUserId(), checkinDay.getCheckinDate());
                return;
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        logDeadLetter(checkinDay, lastException);
    }

    private void logDeadLetter(CheckinDay checkinDay, Exception ex) {
        String entry = String.format("DEAD_LETTER | userId=%d | date=%s | created=%s | error=%s",
                checkinDay.getUserId(),
                checkinDay.getCheckinDate() != null ? checkinDay.getCheckinDate() : "null",
                checkinDay.getCreatedAt(),
                ex != null ? ex.getMessage() : "unknown");
        log.atLevel(Level.ERROR).log(entry);
    }
}
