package com.pwsh.global.log;

import com.pwsh.common.BaseVO;
import com.pwsh.domain.eventlog.service.EventLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 도메인 컨트롤러의 insert/update/delete가 정상 반환되면 event_log에 행위 로그를 남기는 AOP.
 * - event_cd: INSERT/UPDATE/DELETE (로그인은 AuthController에서 LOGIN 직접 기록, 조회는 로깅 안 함)
 * - target_table: 컨트롤러명을 소문자로(= 테이블명), target_id: 요청 VO의 rowId(단건)/rowIds(다건)
 * - 사용자 행위 1건 = 로그 1건(내부 하우스키핑 update는 컨트롤러 진입점이 아니라 제외됨)
 * <p>
 * <b>포착 범위 주의</b>: 메서드명이 정확히 insert/update/delete인 것만 잡는다. {@code updateStatus}처럼
 * {@code {variant}} 변형으로 분리된 메서드는 여기서 잡히지 않으므로, 추적이 필요한 관리자 조치
 * (신고 처리·회원 제재·강제 로그아웃·권한 변경)는 각 서비스가 EventLogService.write로 직접 기록한다.
 * 포인트컷을 update*로 넓히지 않는 이유는 쪽지 읽음·알림설정 같은 일상 행위까지 쌓여
 * 감사 로그의 신호 대 잡음 비가 나빠지기 때문이다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class EventLogAspect {

    private final EventLogService eventLogService;

    @AfterReturning("execution(* com.pwsh.domain..web.*Controller.insert(..))")
    public void onInsert(JoinPoint jp) {
        eventLogService.write("INSERT", targetTable(jp), targetId(jp));
    }

    @AfterReturning("execution(* com.pwsh.domain..web.*Controller.update(..))")
    public void onUpdate(JoinPoint jp) {
        eventLogService.write("UPDATE", targetTable(jp), targetId(jp));
    }

    @AfterReturning("execution(* com.pwsh.domain..web.*Controller.delete(..))")
    public void onDelete(JoinPoint jp) {
        eventLogService.write("DELETE", targetTable(jp), targetId(jp));
    }

    /** PolicyController → policy (테이블명에 접두어가 없으므로 컨트롤러명을 소문자로 그대로 쓴다) */
    private String targetTable(JoinPoint jp) {
        String cls = jp.getTarget().getClass().getSimpleName().replace("Controller", "");
        return cls.toLowerCase();
    }

    /** 요청 VO의 rowId(단건) 또는 rowIds(다건). 신규 등록처럼 없으면 null. */
    private String targetId(JoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof BaseVO vo) {
                if (vo.getRowId() != null && !vo.getRowId().isEmpty()) {
                    return vo.getRowId();
                }
                if (vo.getRowIds() != null && vo.getRowIds().length > 0) {
                    return String.join(",", vo.getRowIds());
                }
            }
        }
        return null;
    }
}
