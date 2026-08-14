package com.pwsh.global.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 서버→클라이언트 실시간 푸시(SSE) 허브.
 *
 * <p>쪽지·알림이 도착하면 해당 수신자의 열린 연결로 바로 밀어준다. 폴링(주기 조회)을 대체하는 것이
 * 목적이므로 <b>이벤트에 본문을 담지 않는다</b> — "새 게 있다"만 알리고 실제 데이터는 클라이언트가
 * 기존 조회 API로 가져간다. 그래야 인가 판정이 한 곳(조회 API)에만 남아 권한 우회 위험이 없다.
 *
 * <p><b>범위 한계</b>: 연결은 이 JVM 안에만 있다. 인스턴스를 2대 이상으로 늘리면 다른 인스턴스에
 * 붙은 사용자에게는 전달되지 않으므로, 그때는 Redis Pub/Sub 같은 브로커를 앞에 둬야 한다.
 * 클라이언트에 폴링 fallback이 남아 있어 푸시가 끊겨도 기능이 멈추지는 않는다.
 */
@Slf4j
@Service
public class RealtimeService {

    /** SSE 연결 수명(ms). 브라우저·프록시가 끊어도 클라이언트가 재연결한다. */
    private static final long TIMEOUT_MS = 10 * 60 * 1000L;

    /** userId → 그 사용자의 열린 연결들(탭 여러 개 가능) */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 연결 등록. 완료·타임아웃·오류 시 스스로 정리한다. */
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        try {
            // 연결 직후 1건 — 클라이언트가 '연결됨'을 확인하고 폴링을 멈출 수 있게
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /**
     * 특정 사용자에게 이벤트 전송(본문 없음, 종류만).
     * 전송 실패한 연결은 끊어진 것으로 보고 제거한다. 실패가 호출자(쪽지 저장 등)를 막지 않는다.
     */
    public void push(String userId, String event) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null || set.isEmpty()) {
            return; // 접속 중이 아니면 보낼 곳이 없다(다음 접속 시 조회로 확인)
        }
        for (SseEmitter em : set) {
            try {
                em.send(SseEmitter.event().name(event).data("1"));
            } catch (Exception e) {
                remove(userId, em);
            }
        }
    }

    /** 연결 유지용 핑 — 유휴 연결을 프록시가 끊는 것을 막는다(주석 이벤트로 트래픽 최소화). */
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emitters.forEach((userId, set) -> {
            for (SseEmitter em : set) {
                try {
                    em.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(userId, em);
                }
            }
        });
    }

    private void remove(String userId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    /** 현재 연결 수(운영 확인용). */
    public int connectionCount() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }
}
