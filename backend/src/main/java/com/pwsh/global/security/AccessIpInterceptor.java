package com.pwsh.global.security;

import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ErrorResponseWriter;
import com.pwsh.domain.accessip.service.AccessIpService;
import com.pwsh.global.web.ClientIpHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 접속 IP 제한 강제 — 환경설정 acc_ip_yn='Y' + access_ip 목록 기준.
 *
 * <p><b>적용 범위를 관리자 계정으로 좁힌 이유.</b> 이 틀은 사용자 사이트의 콘텐츠 API도
 * {@code /api/adm/**}(게시글·댓글·파일)를 함께 쓴다. URL 접두어만으로 막으면
 * 일반 회원의 글쓰기까지 IP로 차단된다. 그래서 <b>관리자 계정(부트스트랩 admin 또는 회원유형
 * MEM02)의 {@code /api/adm/**} 요청</b>만 검사한다 — 보호 대상은 관리 기능이고,
 * 일반 회원·비로그인 사용자는 대상이 아니다.
 *
 * <p>로그인 자체도 {@code AuthService}에서 같은 기준으로 막는다(토큰을 아예 발급하지 않음).
 * 여기서는 이미 발급된 토큰으로 다른 IP에서 접근하는 경우를 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessIpInterceptor implements HandlerInterceptor {

    private final AccessIpService accessIpService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!SecurityUtil.isAdmin()) {
            return true;
        }
        String ip = ClientIpHolder.get();
        if (accessIpService.isAllowed(ip)) {
            return true;
        }
        log.warn("[AccessIp] 허용되지 않은 IP의 관리자 요청 차단 — ip={}, memberId={}, path={}",
                ip, SecurityUtil.getCurrentMemberId(), request.getRequestURI());
        ErrorResponseWriter.write(response, ErrorCode.ACCESS_DENIED, "허용되지 않은 IP에서의 접속입니다. (" + ip + ")");
        return false;
    }
}
