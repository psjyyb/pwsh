package com.pwsh.global.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영(prod) 필수 환경변수 검증 — 미설정 시 기동 실패(fail-fast). dev/기타 프로파일엔 영향 없음.
 * APP_CRYPTO_KEY 미설정 시 BaseVO가 dev 기본키('psjyyb')로 폴백해 t_user PII를 약한 키로 암/복호화하는 사고를 막는다.
 */
@Slf4j
@Component
@Profile("prod")
public class ProdEnvGuard {

    @PostConstruct
    void check() {
        if (isBlank(System.getenv("APP_CRYPTO_KEY"))) {
            throw new IllegalStateException(
                    "운영 환경변수 APP_CRYPTO_KEY가 설정되지 않았습니다. (t_user PII 암호화 키 — 반드시 주입할 것)");
        }
        if (isBlank(System.getenv("FILE_UPLOAD_DIR"))) {
            log.warn("[ProdEnvGuard] FILE_UPLOAD_DIR 미설정 — 업로드가 실행 CWD 상대경로에 저장돼 재배포 시 유실될 수 있습니다. 절대경로 권장.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
