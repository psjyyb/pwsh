package com.pwsh.global.web;

import com.pwsh.common.response.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 버전 조회(공개) — 화면 하단에 표시해 "지금 뜬 게 어느 버전인지"를 눈으로 확인한다.
 *
 * <p>버전은 빌드 시 생성되는 META-INF/build-info.properties(build.gradle의 springBoot.buildInfo())에서 읽는다.
 * IDE에서 클래스만 돌리면 그 파일이 없을 수 있어 {@link ObjectProvider}로 받아 없으면 "dev"로 응답한다
 * (없다고 기동이 실패하면 개발이 불편해진다).
 *
 * <p>공개 경로(/api/pub)에 둔 이유: 로그인 화면·사용자 화면에서도 버전을 봐야 하고,
 * 버전 문자열 자체는 민감정보가 아니다.
 */
@RestController
@RequestMapping("/api/pub/version")
public class VersionController {

    private final BuildProperties buildProperties;

    public VersionController(ObjectProvider<BuildProperties> provider) {
        this.buildProperties = provider.getIfAvailable();
    }

    @RequestMapping
    public ApiResponse<Map<String, String>> version() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("version", buildProperties == null ? "dev" : buildProperties.getVersion());
        body.put("buildTime", buildProperties == null || buildProperties.getTime() == null
                ? "" : buildProperties.getTime().toString());
        return ApiResponse.ok(body);
    }
}
