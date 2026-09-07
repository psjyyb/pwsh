package com.pwsh.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pwsh.common.util.IpMatcher;
import org.junit.jupiter.api.Test;

/**
 * IP 허용 판정 단위테스트. 경계(prefix 0·32·비트 중간)·형식오류·IPv6까지 확인한다.
 * 이 판정이 틀리면 관리자 화면이 열려버리거나(과허용) 스스로 잠긴다(과차단).
 */
class IpMatcherTest {

    @Test
    void exact_match() {
        assertTrue(IpMatcher.matches("192.168.0.10", "192.168.0.10"));
        assertTrue(IpMatcher.matches(" 192.168.0.10 ", " 192.168.0.10 ")); // 앞뒤 공백 허용
        assertFalse(IpMatcher.matches("192.168.0.11", "192.168.0.10"));
        assertTrue(IpMatcher.matches("::1", "::1"));
        // 같은 IPv6 주소의 다른 표기 — 서블릿이 "0:0:0:0:0:0:0:1"로 주는 환경이 있다
        assertTrue(IpMatcher.matches("0:0:0:0:0:0:0:1", "::1"));
        assertTrue(IpMatcher.matches("::1", "0:0:0:0:0:0:0:1"));
        assertTrue(IpMatcher.matches("192.168.000.010", "192.168.0.10")); // 0 채움 표기
        // 같은 주소라도 표기 체계가 다르면(IPv4 vs IPv6 루프백) 일치로 보지 않는다
        assertFalse(IpMatcher.matches("127.0.0.1", "::1"));
    }

    @Test
    void cidr_match() {
        assertTrue(IpMatcher.matches("192.168.0.1", "192.168.0.0/24"));
        assertTrue(IpMatcher.matches("192.168.0.255", "192.168.0.0/24"));
        assertFalse(IpMatcher.matches("192.168.1.1", "192.168.0.0/24"));
        // 바이트 경계가 아닌 prefix — 마스크 계산이 맞는지
        assertTrue(IpMatcher.matches("10.0.0.5", "10.0.0.0/29"));   // 10.0.0.0~7
        assertFalse(IpMatcher.matches("10.0.0.8", "10.0.0.0/29"));
        assertTrue(IpMatcher.matches("10.1.2.3", "10.0.0.0/8"));
        assertFalse(IpMatcher.matches("11.1.2.3", "10.0.0.0/8"));
    }

    @Test
    void cidr_boundary_prefix() {
        assertTrue(IpMatcher.matches("1.2.3.4", "0.0.0.0/0"));       // 전체 허용
        assertTrue(IpMatcher.matches("1.2.3.4", "1.2.3.4/32"));      // 단일 호스트
        assertFalse(IpMatcher.matches("1.2.3.5", "1.2.3.4/32"));
        assertFalse(IpMatcher.matches("1.2.3.4", "1.2.3.4/33"));     // 범위 초과 → 거부
        assertFalse(IpMatcher.matches("1.2.3.4", "1.2.3.4/-1"));
    }

    @Test
    void ipv6_cidr() {
        assertTrue(IpMatcher.matches("2001:db8::1", "2001:db8::/32"));
        assertFalse(IpMatcher.matches("2001:db9::1", "2001:db8::/32"));
        // IPv4 주소를 IPv6 대역과 비교 — 길이가 달라 불일치
        assertFalse(IpMatcher.matches("192.168.0.1", "2001:db8::/32"));
    }

    @Test
    void invalid_input_is_denied() {
        assertFalse(IpMatcher.matches(null, "192.168.0.1"));
        assertFalse(IpMatcher.matches("192.168.0.1", null));
        assertFalse(IpMatcher.matches("", "192.168.0.1"));
        assertFalse(IpMatcher.matches("192.168.0.1", ""));
        assertFalse(IpMatcher.matches("192.168.0.1", "192.168.0.0/abc"));
        assertFalse(IpMatcher.matches("192.168.0.1", "999.999.999.999/24"));
        // 호스트명은 받지 않는다(DNS 조회 회피)
        assertFalse(IpMatcher.matches("192.168.0.1", "localhost"));
        assertFalse(IpMatcher.matches("localhost", "192.168.0.1"));
    }

    @Test
    void rule_validation() {
        assertTrue(IpMatcher.isValidRule("192.168.0.10"));
        assertTrue(IpMatcher.isValidRule("192.168.0.0/24"));
        assertTrue(IpMatcher.isValidRule("::1"));
        assertTrue(IpMatcher.isValidRule("2001:db8::/32"));
        assertFalse(IpMatcher.isValidRule(null));
        assertFalse(IpMatcher.isValidRule(""));
        assertFalse(IpMatcher.isValidRule("   "));
        assertFalse(IpMatcher.isValidRule("192.168.0.256"));
        assertFalse(IpMatcher.isValidRule("192.168.0.0/33"));
        assertFalse(IpMatcher.isValidRule("192.168.0.0/x"));
        assertFalse(IpMatcher.isValidRule("office-pc"));
    }
}
