package com.pwsh.domain.configitem.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.domain.configitem.service.ConfigItemService;
import com.pwsh.domain.configitem.service.ConfigItemVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확장 설정 공개 조회. 사용자 화면(푸터 문구·메인 구성 등)이 비로그인 상태에서도 읽어야 해서 분리했다.
 *
 * <p>{@code public_yn='Y'}로 명시한 항목만 내려간다 — 관리자용 엔드포인트와 같은 목록을 쓰면
 * 나중에 추가되는 내부용 설정까지 비로그인에게 노출된다.
 * 경로를 {@code /api/pub}에 둔 것도 같은 이유로, 공개 대상임을 URL에서 드러내기 위함이다.
 */
@RestController
@RequestMapping("/api/pub/configitem")
@RequiredArgsConstructor
public class PubConfigItemController {

    private final ConfigItemService configItemService;

    @RequestMapping
    public ApiResponse<List<ConfigItemVO>> selectListPublic() {
        return ApiResponse.ok(configItemService.selectListPublic());
    }
}
