package com.spot.scheduler;

import com.spot.domain.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionScheduler.class);

    private final SessionService sessionService;

    public SessionScheduler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 매시 정각 KST — 계정별 study day가 지난 OPEN/PAUSED 세션을 경계 시각으로 강제 종료한다.
     * (계정별 reset hour 대응; 기존 매일 06:00 단일 cron 대체)
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void closeCrossDaySessions() {
        int closed = sessionService.closeCrossDaySessions();
        if (closed > 0) {
            log.info("[CloseCrossDaySessions] closed={}", closed);
        }
    }
}
