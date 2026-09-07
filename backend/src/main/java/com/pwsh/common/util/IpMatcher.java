package com.pwsh.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 클라이언트 IP가 허용 항목과 맞는지 판정한다.
 *
 * <p>허용 항목은 두 가지 형태를 받는다.
 * <ul>
 *   <li>정확히 일치 — {@code 192.168.0.10}, {@code ::1}</li>
 *   <li>CIDR 대역 — {@code 192.168.0.0/24}, {@code 2001:db8::/32}</li>
 * </ul>
 *
 * <p>IPv4/IPv6를 모두 다루기 위해 문자열이 아니라 바이트 배열로 비교한다.
 * 주소 길이가 다르면(예: IPv4 vs IPv6) 무조건 불일치로 본다 — 형태가 다른 주소를
 * 억지로 변환해 맞추면 의도치 않게 허용될 수 있다.
 */
public final class IpMatcher {

    private IpMatcher() {
    }

    /**
     * 허용 여부.
     *
     * @param clientIp 검사 대상 IP(요청자)
     * @param rule     허용 항목(정확일치 또는 CIDR)
     * @return 규칙·IP가 잘못된 형식이면 false — 판단 불가는 허용하지 않는다
     */
    public static boolean matches(String clientIp, String rule) {
        if (clientIp == null || rule == null) {
            return false;
        }
        String ip = clientIp.trim();
        String r = rule.trim();
        if (ip.isEmpty() || r.isEmpty()) {
            return false;
        }
        int slash = r.indexOf('/');
        if (slash < 0) {
            // 같은 주소라도 표기가 다를 수 있다(IPv6 루프백이 "::1"로도 "0:0:0:0:0:0:0:1"로도 온다)
            // → 문자열이 아니라 바이트로 비교한다. 파싱이 안 되면 문자열 비교로 물러선다.
            byte[] a = toBytes(ip);
            byte[] b = toBytes(r);
            if (a != null && b != null) {
                return java.util.Arrays.equals(a, b);
            }
            return ip.equalsIgnoreCase(r);
        }
        String network = r.substring(0, slash);
        int prefixLen;
        try {
            prefixLen = Integer.parseInt(r.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] ipBytes = toBytes(ip);
        byte[] netBytes = toBytes(network);
        if (ipBytes == null || netBytes == null || ipBytes.length != netBytes.length) {
            return false;
        }
        int maxBits = ipBytes.length * 8;
        if (prefixLen < 0 || prefixLen > maxBits) {
            return false;
        }
        return sameNetwork(ipBytes, netBytes, prefixLen);
    }

    /**
     * 허용 항목으로 저장 가능한 형식인지 — 등록/수정 시 입력 검증에 쓴다.
     * 잘못된 값을 저장하면 {@link #matches}가 항상 false가 되어 "등록했는데 안 되는" 상태가 된다.
     */
    public static boolean isValidRule(String rule) {
        if (rule == null) {
            return false;
        }
        String r = rule.trim();
        if (r.isEmpty()) {
            return false;
        }
        int slash = r.indexOf('/');
        if (slash < 0) {
            return toBytes(r) != null;
        }
        byte[] netBytes = toBytes(r.substring(0, slash));
        if (netBytes == null) {
            return false;
        }
        try {
            int prefixLen = Integer.parseInt(r.substring(slash + 1).trim());
            return prefixLen >= 0 && prefixLen <= netBytes.length * 8;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** prefixLen 비트까지 동일한지 — 앞의 온전한 바이트들을 먼저 비교하고, 남은 비트는 마스크로 본다. */
    private static boolean sameNetwork(byte[] a, byte[] b, int prefixLen) {
        int fullBytes = prefixLen / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        int remainingBits = prefixLen % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (a[fullBytes] & mask) == (b[fullBytes] & mask);
    }

    /** 호스트명 조회를 피하기 위해 숫자 주소만 허용한다(DNS 조회는 느리고 스푸핑 위험이 있다). */
    private static byte[] toBytes(String address) {
        if (!address.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
