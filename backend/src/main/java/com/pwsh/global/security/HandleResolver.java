package com.pwsh.global.security;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.member.service.MemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공개 식별자(handle) ↔ 로그인 ID(member_id) 변환.
 * 사용자 화면·공개 API는 handle로만 회원을 지목하고, 서버 내부(권한·audit·FK)는 로그인 ID를 쓴다.
 * 그 경계에서 변환을 담당한다(각 도메인 서비스가 중복 구현하지 않도록 공용).
 */
@Component
@RequiredArgsConstructor
public class HandleResolver {

    private final CommonDAO commonDAO;

    /** handle → 로그인 ID. 없거나 비활성 회원이면 404. */
    public String toMemberId(String handle) {
        if (handle == null || handle.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 회원이 없습니다.");
        }
        MemberVO p = new MemberVO();
        p.setHandle(handle);
        MemberVO u = commonDAO.selectOne("memberDAO.selectByHandle", p);
        if (u == null || !"Y".equals(u.getUseYn())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        return u.getMemberId();
    }

    /** 로그인 ID → handle. 없으면 null(표시용이므로 예외 없이 처리). */
    public String toHandle(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return null;
        }
        MemberVO p = new MemberVO();
        p.setMemberId(memberId);
        MemberVO u = commonDAO.selectOne("memberDAO.selectByMemberId", p);
        return u == null ? null : u.getHandle();
    }
}
