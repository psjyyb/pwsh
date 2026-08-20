package com.pwsh.global.config;

import com.pwsh.common.BaseVO;
import com.pwsh.global.security.SecurityUtil;
import com.pwsh.global.web.ClientIpHolder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

/**
 * MyBatis Interceptor. INSERT/UPDATE 시 BaseVO의 audit(reg_id/upd_id/reg_ip/upd_ip)을 자동 세팅.
 * 로그인 사용자(SecurityContext) + 요청 IP(ClientIpHolder). 컨텍스트 없으면 user="system".
 * IP는 스케줄 배치처럼 HTTP 요청이 없는 경로에서 null인데, reg_ip/upd_ip는 NOT NULL이라
 * 그대로 두면 insert가 실패한다 → 로컬(127.0.0.1)로 채워 배치 적재가 조용히 실패하지 않게 한다.
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInterceptor implements Interceptor {

    /** 요청 컨텍스트가 없는 실행(스케줄 배치·기동 초기화)의 audit IP. */
    private static final String LOCAL_IP = "127.0.0.1";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object param = invocation.getArgs()[1];
        SqlCommandType type = ms.getSqlCommandType();

        if (param instanceof BaseVO vo
                && (type == SqlCommandType.INSERT || type == SqlCommandType.UPDATE)) {
            String memberId = SecurityUtil.getCurrentMemberId();
            String ip = ClientIpHolder.get();
            if (type == SqlCommandType.INSERT) {
                vo.setRegId(memberId);
                if (ip != null) {
                    vo.setRegIp(ip);
                } else if (vo.getRegIp() == null || vo.getRegIp().isBlank()) {
                    vo.setRegIp(LOCAL_IP);
                }
            }
            vo.setUpdId(memberId);
            if (ip != null) {
                vo.setUpdIp(ip);
            } else if (vo.getUpdIp() == null || vo.getUpdIp().isBlank()) {
                vo.setUpdIp(LOCAL_IP);
            }
        }
        return invocation.proceed();
    }
}
