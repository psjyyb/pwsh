package com.pwsh.global.config;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.user.service.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 초기 데이터 생성. 관리자 계정(admin)이 없으면 생성(비밀번호 BCrypt).
 * (부팅 시점이라 audit은 수동 세팅: system/127.0.0.1)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CommonDAO commonDAO;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createIfAbsent("admin", "admin1234!", "MEM02", "관리자", "관리자");
        // 샘플 일반 사용자(권한그룹 예시용). 권한그룹/메뉴 매핑은 data.sql에서 시드.
        createIfAbsent("user", "user1234!", "MEM01", "일반사용자", "회원1");
    }

    private void createIfAbsent(String userId, String rawPw, String memCd, String userNm, String nickname) {
        UserVO check = new UserVO();
        check.setUserId(userId);
        Integer count = commonDAO.selectOne("userDAO.selectCount", check);
        if (count != null && count > 0) {
            return;
        }
        UserVO user = new UserVO();
        user.setUserId(userId);
        user.setUserPw(passwordEncoder.encode(rawPw));
        user.setMemCd(memCd);
        user.setUserNm(userNm);
        user.setNickname(nickname);
        user.setStatusCd("STATUS01");
        user.setRegId("system");
        user.setUpdId("system");
        user.setRegIp("127.0.0.1");
        user.setUpdIp("127.0.0.1");
        commonDAO.insert("userDAO.insert", user);
        // 보안: raw 비밀번호는 로그에 남기지 않는다(로그 유출 시 계정 탈취). 기본 비번은 최초 로그인 후 변경 전제.
        log.info("[DataInitializer] 기본 계정 생성: {} (기본 비밀번호는 최초 로그인 후 변경 필요)", userId);
    }
}
