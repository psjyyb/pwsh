package com.pwsh.global.security;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 엔드포인트 권한 강제. /api/adm/{domain}/... 요청을 메뉴 link_url(/adm/{domain})에 매핑해
 * 로그인 사용자가 t_auth 권한(그룹)으로 그 메뉴 접근권을 가졌는지 검사(없으면 403).
 * - 슈퍼관리자(userId=admin) 전면 허용
 * - 공용/인프라 엔드포인트(코드콤보·메뉴트리·설정조회·이벤트로그기록·권한그룹콤보·파일)는 예외 허용
 * - 매핑되는 메뉴가 없거나 권한이 없으면 거부(fail-closed). 신규 /adm 도메인은 메뉴(link_url=/adm/{domain}) 등록 필요.
 *   조회/수정은 구분하지 않는다(메뉴 접근권 유무만). superYn(admin)·예외 목록·file/bbs/comment(콘텐츠)는 위에서 선허용.
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final CommonDAO commonDAO;

    /** 권한 무관 허용(공용 조회·인프라). 경로 suffix 매칭 */
    private static final Set<String> EXEMPT_SUFFIX = Set.of(
            "/code/selectCodeListCombo.do",
            "/menu/selectMenuListTree.do",
            "/config/selectConfigView.do",
            "/page/selectPageView.do",
            "/popup/selectPopupListMain.do",
            "/bbsinfo/selectBbsinfoView.do",
            "/bbsinfo/selectBbsinfoListCombo.do",
            "/hobby/selectHobbyList.do",
            "/hobby/selectHobbyView.do",
            "/authgrp/selectAuthgrpListCombo.do");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/adm/")) {
            return true;
        }

        String userId = SecurityUtil.getCurrentUserId();
        // 부트스트랩 슈퍼관리자 전면 허용(잠금 방지)
        if ("admin".equals(userId)) {
            return true;
        }
        // 공용/인프라 예외: 파일, 게시글/댓글, 모집/신청(사용자 콘텐츠 — 로그인만 필요, 메뉴권한 무관.
        // 소유자/상태 등 세부 인가는 각 Service가 담당)
        if (path.startsWith("/api/adm/file/")
                || path.startsWith("/api/adm/bbs/")
                || path.startsWith("/api/adm/comment/")
                || path.startsWith("/api/adm/recruit/")
                || path.startsWith("/api/adm/recruitApply/")
                || path.startsWith("/api/adm/recruitChat/")
                || path.startsWith("/api/adm/userHobby/")
                || path.startsWith("/api/adm/notification/")
                || path.startsWith("/api/adm/like/")
                || path.startsWith("/api/adm/search/")
                || path.startsWith("/api/adm/report/")
                || path.startsWith("/api/adm/message/")
                || path.startsWith("/api/adm/review/")
                || path.startsWith("/api/adm/bookmark/")
                || path.startsWith("/api/adm/block/")
                || path.startsWith("/api/adm/follow/")
                || path.startsWith("/api/adm/feed/")   // 내 취미 피드 — 본인 기준 집계(서비스에서 로그인 강제)
                || path.startsWith("/api/adm/stats/")) { // stats는 대응 메뉴 없음 — 서비스에서 관리자 인가
            return true;
        }
        for (String s : EXEMPT_SUFFIX) {
            if (path.endsWith(s)) {
                return true;
            }
        }

        // /api/adm/{domain}/... → 메뉴 link_url = /adm/{domain}
        String rest = path.substring("/api/adm/".length());
        int slash = rest.indexOf('/');
        String domain = slash > 0 ? rest.substring(0, slash) : rest;
        if (domain.isEmpty()) {
            return true;
        }
        String linkUrl = "/adm/" + domain;

        Integer cnt = commonDAO.selectOne("menuDAO.selectPermittedCount",
                Map.of("userId", userId == null ? "" : userId, "linkUrl", linkUrl));
        if (cnt != null && cnt > 0) {
            return true;
        }

        deny(response);
        return false;
    }

    private void deny(HttpServletResponse response) throws IOException {
        ErrorResponseWriter.write(response, ErrorCode.ACCESS_DENIED);
    }
}
