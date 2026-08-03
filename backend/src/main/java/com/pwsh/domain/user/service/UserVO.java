package com.pwsh.domain.user.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 사용자 VO (t_user). 로그인/조회에 필요한 필드. (필드 String 통일)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserVO extends BaseVO {

    private String userId;
    private String userPw;
    private String memCd;
    private String userNm;
    private String nickname; // 커뮤니티 표시명(평문). 셀프가입 필수, 작성자 표기용
    private String profileFileId; // 프로필 사진 파일(t_file.file_id 직접 참조)
    private String phone;
    private String email;
    private String genCd;
    private String birth;
    private String statusCd;
    private String memCdNm;    // 회원유형명(t_code 조인, 목록 표시용)
    private String statusCdNm; // 계정상태명(t_code 조인, 목록 표시용)

    // 로그인/비밀번호 정책 (last_upd_dt=비번 최종변경일, pw_expire_dt=만료일)
    private String lastLoginIp;
    private String pwExpireDt;
    private String pwExpired;   // 조회 시 계산: 'Y'=만료됨
    private String pwDaysLeft;  // 만료까지 남은 일수(만료 시 0)
    private String lockActive;    // 조회 시 계산: 'Y'=잠금 유효(잠금시간 미경과)
    private String failCnt;       // 로그인 실패 누적 횟수
    private String tokenVer;      // 토큰 버전(로그인 시 +1). JWT ver 클레임과 대조 → 단일세션·로그아웃 무효화
    private String failCntLimit;  // 잠금 기준 실패 횟수(t_config)
    private String failCntDeniedTi; // 잠금 시간(분, t_config)
    private String lockRemainMin; // 잠금 잔여 시간(분, 계산값)

    // 권한그룹 매핑(t_auth_user)용
    private String[] authgrpIds; // 저장 시 선택된 그룹 목록
    private String authgrpId;     // 개별 insert용
}
