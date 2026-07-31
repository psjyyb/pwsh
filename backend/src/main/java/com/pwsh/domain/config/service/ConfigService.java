package com.pwsh.domain.config.service;

import com.pwsh.common.CommonDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 환경설정(단일 행) 업무 로직. 컨트롤러는 매핑만(단일 @Service). */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final CommonDAO commonDAO;

    public ConfigVO selectView() {
        return commonDAO.selectOne("configDAO.selectView", new ConfigVO());
    }

    public void update(ConfigVO vo) {
        commonDAO.update("configDAO.update", vo);
    }
}
