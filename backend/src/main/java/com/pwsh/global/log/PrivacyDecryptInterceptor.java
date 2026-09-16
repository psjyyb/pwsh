package com.pwsh.global.log;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 개인정보 복호화 조회를 탐지해 {@link PrivacyAccessCollector}에 모으는 MyBatis 인터셉터.
 *
 * <p><b>왜 애너테이션이나 목록이 아니라 SQL을 보는가:</b> "이 조회는 개인정보다"를 개발자가 선언하게 하면
 * 새 화면을 만들 때 빠뜨린다 — 그리고 빠진 것은 아무도 모른다(컴파일러도 테스트도 못 잡는다).
 * 이 틀에서 개인정보는 <b>반드시</b> pgcrypto로 복호화해서 읽으므로, 실행 SQL에 {@code DECRYPT(}가
 * 있으면 그게 곧 개인정보 조회다. 매퍼를 새로 쓰든 파생 프로젝트가 추가하든 자동으로 잡힌다.
 *
 * <p>비용: 요청마다 BoundSql을 한 번 더 만든다(동적 SQL 재평가). MappedStatement 단위로 캐시하지
 * <b>않는</b> 이유는 {@code <if>} 안에만 DECRYPT가 있는 매퍼가 실제로 있기 때문이다
 * (예: memberDAO.selectListTotalCount는 이름 검색어가 있을 때만 복호화한다) — 캐시하면 그 검색이
 * 기록에서 빠진다.
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class PrivacyDecryptInterceptor implements Interceptor {

    /** pgcrypto 복호화 표식. 이 틀에서 개인정보를 평문으로 읽는 유일한 방법이다 */
    private static final String DECRYPT_MARK = "DECRYPT(";
    /** 대상 회원 ID를 뽑을 때 찾는 getter. 없으면 "대상 불명"으로 남긴다 */
    private static final String TARGET_GETTER = "getMemberId";
    /** 대량 결과에서 대상 ID를 훑는 상한(로그 1건에 다 담지도 못한다 — 훑는 비용만 커진다) */
    private static final int SCAN_LIMIT = 200;

    /** 결과 타입별 getMemberId 조회 캐시(없으면 NONE). 매 행마다 리플렉션을 반복하지 않기 위함 */
    private static final Map<Class<?>, Method> TARGET_GETTERS = new ConcurrentHashMap<>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("toString"); // "없음" 표식용 더미
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        // HTTP 요청 밖(기동·스케줄러)은 취급자도 화면도 없다 — 접근기록으로서 의미가 없다
        if (RequestContextHolder.getRequestAttributes() == null) {
            return result;
        }
        try {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            Object param = invocation.getArgs()[1];
            if (!ms.getBoundSql(param).getSql().contains(DECRYPT_MARK)) {
                return result;
            }
            List<?> rows = result instanceof List<?> list ? list : List.of();
            PrivacyAccessCollector.add(ms.getId(), targetsOf(rows), rows.size());
        } catch (Exception e) {
            // 기록에 실패해도 조회 자체는 정상 반환한다 — 로그 때문에 업무가 멈추면 안 된다.
            // (실패가 잦다면 로그 배선이 깨진 것이므로 PrivacyLogTest가 먼저 실패한다)
        }
        return result;
    }

    /** 결과 행에서 대상 회원 ID 수집. 회원 VO가 아니면 비어 있는 집합(=대상 불명). */
    private Set<String> targetsOf(List<?> rows) {
        Set<String> targets = new LinkedHashSet<>();
        int scanned = 0;
        for (Object row : rows) {
            if (row == null || scanned++ >= SCAN_LIMIT) {
                continue;
            }
            Method getter = targetGetter(row.getClass());
            if (getter == null) {
                return Set.of(); // 이 조회는 회원 단위로 특정할 수 없다
            }
            try {
                Object v = getter.invoke(row);
                if (v instanceof String s && !s.isBlank()) {
                    targets.add(s);
                }
            } catch (Exception ignored) {
                return Set.of();
            }
        }
        return targets;
    }

    private Method targetGetter(Class<?> type) {
        Method cached = TARGET_GETTERS.computeIfAbsent(type, t -> {
            try {
                return t.getMethod(TARGET_GETTER);
            } catch (NoSuchMethodException e) {
                return NONE;
            }
        });
        return cached == NONE ? null : cached;
    }
}
