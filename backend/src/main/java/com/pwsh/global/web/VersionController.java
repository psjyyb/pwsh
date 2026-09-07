package com.pwsh.global.web;

import com.pwsh.common.response.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 버전 조회(공개). 관리자 화면 하단에 표시해 "지금 이 서버가 어느 버전인지"를 눈으로 확인한다.
 *
 * <p>내려주는 값 두 가지.
 * <ul>
 *   <li>{@code version} — 이 프로젝트 자체의 버전</li>
 *   <li>{@code cmsVersion} — 바탕이 되는 CMS(틀)의 버전. 이 저장소는 곧 CMS이므로 version과 같지만,
 *       이 틀을 복사해 만든 파생 프로젝트에서는 "어느 CMS 버전까지 흡수했는지"가 되어 값이 달라진다.
 *       두 값을 함께 내려야 파생 프로젝트가 CMS와 얼마나 벌어졌는지 화면에서 바로 알 수 있다.</li>
 * </ul>
 *
 * <p>값은 빌드 때 생성되는 META-INF/build-info.properties(build.gradle의 springBoot.buildInfo())에서 읽는다.
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
        String version = buildProperties == null ? "dev" : buildProperties.getVersion();
        body.put("version", version);
        // buildInfo의 additional 항목. 예전 빌드 산출물에는 없을 수 있어 없으면 version으로 대체한다.
        String cms = buildProperties == null ? "dev" : buildProperties.get("cmsVersion");
        body.put("cmsVersion", cms == null ? version : cms);
        body.put("buildTime", buildProperties == null || buildProperties.getTime() == null
                ? "" : buildProperties.getTime().toString());
        return ApiResponse.ok(body);
    }
}
