package com.pwsh.domain.config.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.global.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 환경설정(단일 행) 업무 로직. 컨트롤러는 매핑만(단일 @Service). */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final CommonDAO commonDAO;

    /**
     * 설정 조회. 사이트명·로고는 로그인 화면과 사용자 화면도 필요해서 이 API는 권한 예외로 열려 있다
     * (PermissionInterceptor의 EXEMPT_SUFFIX). 그래서 관리자가 아니면 표시용 두 값만 돌려준다 —
     * 잠금 임계값·비번 만료일 같은 보안 정책값을 아무 방문자에게 알려줄 이유가 없다.
     */
    public ConfigVO selectView() {
        ConfigVO vo = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        if (vo == null || SecurityUtil.isAdmin()) {
            return vo;
        }
        ConfigVO display = new ConfigVO();
        display.setTitle(vo.getTitle());
        display.setLogoFileId(vo.getLogoFileId());
        return display;
    }

    public void update(ConfigVO vo) {
        commonDAO.update("configDAO.update", vo);
    }
}
