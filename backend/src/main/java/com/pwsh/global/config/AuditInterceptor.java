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
 * 로그인 사용자(SecurityContext) + 요청 IP(ClientIpHolder). 컨텍스트 없으면 user="system", ip는 미변경.
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object param = invocation.getArgs()[1];
        SqlCommandType type = ms.getSqlCommandType();

        if (param instanceof BaseVO vo
                && (type == SqlCommandType.INSERT || type == SqlCommandType.UPDATE)) {
            String userId = SecurityUtil.getCurrentUserId();
            String ip = ClientIpHolder.get();
            if (type == SqlCommandType.INSERT) {
                vo.setRegId(userId);
                if (ip != null) {
                    vo.setRegIp(ip);
                }
            }
            vo.setUpdId(userId);
            if (ip != null) {
                vo.setUpdIp(ip);
            }
        }
        return invocation.proceed();
    }
}
