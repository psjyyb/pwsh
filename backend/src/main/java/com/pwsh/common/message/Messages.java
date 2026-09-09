package com.pwsh.common.message;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

/**
 * 사용자 노출 메시지 조회. 문구는 {@code messages.properties}에 있고 코드는 키만 안다.
 *
 * <p><b>왜 static 접근인가.</b> 메시지가 필요한 곳에 {@code Validate}(static 유틸)처럼
 * 빈이 아닌 자리가 섞여 있다. 그 몇 곳 때문에 모든 호출부에 MessageSource를 주입해 넘기면
 * 시그니처가 지저분해지므로, 기동 시 한 번 주입받아 static으로 들고 쓴다.
 * 이 저장소는 {@code SecurityUtil}·{@code ClientIpHolder}도 같은 방식이라 낯선 패턴이 아니다.
 *
 * <p>키를 못 찾으면 <b>키 문자열을 그대로 반환하고 경고를 남긴다</b>. 빈 문자열을 내보내면
 * 화면에 아무 안내도 안 뜨고 원인 추적이 어려워지므로, 눈에 보이게 하는 쪽을 택했다.
 */
@Slf4j
@Component
public class Messages {

    private static MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        Messages.messageSource = messageSource;
    }

    /**
     * 메시지 조회.
     *
     * @param key  messages.properties의 키
     * @param args 값에 {@code {0}} {@code {1}}이 있을 때 채울 인자
     */
    public static String get(String key, Object... args) {
        String found = find(key, args);
        if (found == null) {
            log.warn("[Messages] 메시지 키를 찾을 수 없다 — key={} (messages.properties 확인)", key);
            return key;
        }
        return found;
    }

    /**
     * 있으면 쓰고 없으면 넘어가는 <b>선택적</b> 조회 — 없는 것이 정상인 경우에 쓴다.
     * (예: {@code ErrorCode}의 기본 메시지를 properties로 덮어쓸 수 있게 해둔 자리.
     * 재정의가 없는 게 보통이라 {@link #get}으로 조회하면 경고 로그만 쌓인다)
     *
     * @return 없으면 null
     */
    public static String find(String key, Object... args) {
        if (messageSource == null) {
            return null; // 스프링 컨텍스트 밖(단위테스트 등)
        }
        try {
            return messageSource.getMessage(key, args, Locale.getDefault());
        } catch (NoSuchMessageException e) {
            return null;
        }
    }
}
