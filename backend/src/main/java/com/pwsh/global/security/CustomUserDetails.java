package com.pwsh.global.security;

import com.pwsh.domain.user.service.UserVO;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security UserDetails 구현. UserVO를 감싼다.
 * 권한은 회원유형(mem_cd) 기반 ROLE_{memCd}. 활성화는 use_yn='Y'.
 */
public class CustomUserDetails implements UserDetails {

    private final UserVO user;

    public CustomUserDetails(UserVO user) {
        this.user = user;
    }

    public String getUserId() {
        return user.getUserId();
    }

    public String getMemCd() {
        return user.getMemCd();
    }

    /** 현재 토큰 버전(t_user.token_ver). JWT ver 클레임과 대조. */
    public String getTokenVer() {
        return user.getTokenVer();
    }

    /** 비밀번호 만료 여부('Y'/'N', 조회 시 계산) */
    public String getPwExpired() {
        return user.getPwExpired();
    }

    /** 비밀번호 만료까지 남은 일수 */
    public String getPwDaysLeft() {
        return user.getPwDaysLeft();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getMemCd()));
    }

    @Override
    public String getPassword() {
        return user.getUserPw();
    }

    @Override
    public String getUsername() {
        return user.getUserId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "Y".equals(user.getUseYn());
    }
}
