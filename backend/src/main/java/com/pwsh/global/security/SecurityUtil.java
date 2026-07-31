package com.pwsh.global.security;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext 조회 공통 유틸. (AuditInterceptor·로그 등 2곳 이상에서 사용)
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /** 현재 로그인 사용자 ID. 미인증이면 "system". */
    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return "system";
    }

    /** 현재 로그인 사용자의 회원유형 코드(mem_cd). 미인증이면 null. */
    public static String getCurrentMemCd() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemCd();
        }
        return null;
    }

    /** 로그인 여부. getCurrentUserId()는 미인증 시 "system"을 반환하므로 그 경우도 미로그인으로 본다. */
    public static boolean isAuthenticated() {
        String userId = getCurrentUserId();
        return userId != null && !"system".equals(userId);
    }

    /** 관리자 여부(부트스트랩 admin 또는 회원유형 MEM02). 소유자/관리자 인가 판정 공통 기준. */
    public static boolean isAdmin() {
        return "admin".equals(getCurrentUserId()) || "MEM02".equals(getCurrentMemCd());
    }

    /** 작성자 본인 또는 관리자만 허용(게시글·댓글 수정/삭제 IDOR 방지). 아니면 403. */
    public static void assertOwnerOrAdmin(String ownerRegId) {
        if (isAdmin()) {
            return;
        }
        String me = getCurrentUserId();
        if (me == null || "system".equals(me) || !me.equals(ownerRegId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
