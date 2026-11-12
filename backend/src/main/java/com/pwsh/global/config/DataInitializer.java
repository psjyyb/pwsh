package com.pwsh.global.config;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.member.service.MemberVO;
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

    /** 기본 계정 비밀번호 — 설치 점검(SystemService.checks)이 "아직 이 값 그대로인지"를 같은 상수로 본다 */
    public static final String DEFAULT_ADMIN_PW = "admin1234!";
    public static final String DEFAULT_USER_PW = "user1234!";

    private final CommonDAO commonDAO;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createIfAbsent("admin", DEFAULT_ADMIN_PW, "MEM02", "관리자", "관리자");
        // 샘플 일반 사용자(권한그룹 예시용). 권한그룹/메뉴 매핑은 마이그레이션(V2 기초데이터)에서 시드.
        createIfAbsent("user", DEFAULT_USER_PW, "MEM01", "일반사용자", "회원1");
    }

    private void createIfAbsent(String memberId, String rawPw, String typeCd, String memberName, String nickname) {
        MemberVO check = new MemberVO();
        check.setMemberId(memberId);
        Integer count = commonDAO.selectOne("memberDAO.selectCount", check);
        if (count != null && count > 0) {
            return;
        }
        MemberVO user = new MemberVO();
        user.setMemberId(memberId);
        user.setPassword(passwordEncoder.encode(rawPw));
        user.setTypeCd(typeCd);
        user.setMemberName(memberName);
        user.setNickname(nickname);
        user.setStatusCd("STATUS01");
        user.setRegId("system");
        user.setUpdId("system");
        user.setRegIp("127.0.0.1");
        user.setUpdIp("127.0.0.1");
        commonDAO.insert("memberDAO.insert", user);
        // 보안: raw 비밀번호는 로그에 남기지 않는다(로그 유출 시 계정 탈취). 기본 비번은 최초 로그인 후 변경 전제.
        log.info("[DataInitializer] 기본 계정 생성: {} (기본 비밀번호는 최초 로그인 후 변경 필요)", memberId);
    }
}
