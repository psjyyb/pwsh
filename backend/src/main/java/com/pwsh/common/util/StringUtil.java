package com.pwsh.common.util;

/**
 * 문자열 공통 유틸. (표준 CMS CommonStrUtil 역할 — 컨트롤러/서비스 전역 재사용)
 */
public final class StringUtil {

    private StringUtil() {
    }

    /** null 또는 빈 문자열 여부 */
    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** 값이 존재하는지(비어있지 않은지) */
    public static boolean isNotEmpty(String s) {
        return !isEmpty(s);
    }
}
