package com.pwsh.global.web;

/**
 * 요청 스레드의 클라이언트 IP 보관(ThreadLocal).
 * ClientIpInterceptor가 set/clear, AuditInterceptor가 get.
 */
public final class ClientIpHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private ClientIpHolder() {
    }

    public static void set(String ip) {
        HOLDER.set(ip);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
