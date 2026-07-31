package com.pwsh.global.security;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.user.service.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * t_user에서 사용자를 조회해 UserDetails로 반환. (MyBatis CommonDAO 사용)
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final CommonDAO commonDAO;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        UserVO param = new UserVO();
        param.setUserId(userId);
        UserVO user = commonDAO.selectOne("userDAO.selectByUserId", param);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId);
        }
        return new CustomUserDetails(user);
    }
}
