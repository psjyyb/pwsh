package com.pwsh.global.security;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.member.service.MemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * member에서 사용자를 조회해 UserDetails로 반환. (MyBatis CommonDAO 사용)
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final CommonDAO commonDAO;

    @Override
    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {
        MemberVO param = new MemberVO();
        param.setMemberId(memberId);
        MemberVO user = commonDAO.selectOne("memberDAO.selectByMemberId", param);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + memberId);
        }
        return new CustomUserDetails(user);
    }
}
