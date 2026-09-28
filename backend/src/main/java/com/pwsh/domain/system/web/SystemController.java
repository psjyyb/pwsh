package com.pwsh.domain.system.web;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ApiResponse;
import com.pwsh.domain.system.service.SystemService;
import com.pwsh.global.security.SecurityUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시스템 상태 · 캐시 · 설정 백업 — 컨트롤러는 매핑만, 로직은 {@link SystemService}.
 * selectSystemView: 상태 한 장 / updateSystemCache: 캐시 비우기 / downloadBackup: 설정 스냅샷
 *
 * <p>⚠ 여기서 내려주는 값(경로·DB 크기·버전·마이그레이션 이력)은 <b>공격자에게 쓸모 있는 정보</b>다.
 * 메뉴권한만 믿지 않고 관리자 여부를 직접 확인한다 — 이 도메인은 메뉴를 지워도 URL이 살아 있으면
 * 안 되기 때문이다.
 */
@RestController
@RequestMapping("/api/adm/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    private void requireAdmin() {
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    @RequestMapping("/selectSystemView.do")
    public ApiResponse<Map<String, Object>> selectView() {
        requireAdmin();
        return ApiResponse.ok(systemService.status());
    }

    /** 캐시 전부 비우기 → 비운 캐시 수 */
    @RequestMapping("/updateSystemCache.do")
    public ApiResponse<Integer> evictCache() {
        requireAdmin();
        return ApiResponse.ok(systemService.evictAll());
    }

    /**
     * 설정 스냅샷 내려받기(JSON).
     *
     * <p>GET인 이유는 브라우저가 파일로 저장하게 하기 위해서다. 그래서 <b>Authorization 헤더를
     * 못 싣는다</b> — 프론트가 axios로 blob을 받아 저장한다(파일 다운로드와 같은 방식).
     */
    @GetMapping("/downloadBackup.do")
    public ResponseEntity<Map<String, Object>> downloadBackup() {
        requireAdmin();
        String name = "config-backup-" + java.time.LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(systemService.configBackup());
    }
}
