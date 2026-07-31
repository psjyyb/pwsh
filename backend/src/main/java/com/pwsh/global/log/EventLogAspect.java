package com.pwsh.global.log;

import com.pwsh.common.BaseVO;
import com.pwsh.domain.eventlog.service.EventLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 도메인 컨트롤러의 insert/update/delete가 정상 반환되면 t_event_log에 행위 로그를 남기는 AOP.
 * - event_type: INSERT/UPDATE/DELETE (로그인은 AuthController에서 LOGIN 직접 기록, 조회는 로깅 안 함)
 * - target_table: 컨트롤러명 → t_xxx, target_id: 요청 VO의 dbKey(단건)/dbKeys(다건)
 * - 사용자 행위 1건 = 로그 1건(내부 하우스키핑 update는 컨트롤러 진입점이 아니라 제외됨)
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

    /** PolicyController → t_policy */
    private String targetTable(JoinPoint jp) {
        String cls = jp.getTarget().getClass().getSimpleName().replace("Controller", "");
        return "t_" + cls.toLowerCase();
    }

    /** 요청 VO의 dbKey(단건) 또는 dbKeys(다건). 신규 등록처럼 없으면 null. */
    private String targetId(JoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof BaseVO vo) {
                if (vo.getDbKey() != null && !vo.getDbKey().isEmpty()) {
                    return vo.getDbKey();
                }
                if (vo.getDbKeys() != null && vo.getDbKeys().length > 0) {
                    return String.join(",", vo.getDbKeys());
                }
            }
        }
        return null;
    }
}
