package com.pwsh.domain.configitem.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확장 설정(키-값) 조회·저장. 컨트롤러는 매핑만, 로직은 여기(단일 @Service).
 *
 * <p><b>왜 이 테이블이 따로 있나.</b> 설정 항목을 하나 늘릴 때마다 컬럼 + VO 필드 + 매퍼 select/update
 * + 화면 Form.Item을 손대야 했다. 여기 행 하나를 넣으면 화면이 정의(name·input_type·group_cd)를
 * 보고 자동으로 그려주므로 코드 수정이 없다.
 *
 * <p>값이 전부 문자열이라 <b>읽는 쪽이 타입을 책임진다</b>. {@link #getInt}·{@link #getBool}은
 * 변환에 실패하면 예외를 던지지 않고 <b>기본값으로 떨어지고 경고를 남긴다</b> —
 * 설정값 오타 하나로 기능이 죽는 것보다 기본값으로 도는 편이 안전하다.
 * (반대로 로그인 잠금처럼 틀린 값이 곧 장애가 되는 정책값은 {@code config} 컬럼에 그대로 둔다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigItemService {

    /** 값 조회는 화면·로직 여러 곳에서 반복되므로 캐시한다. 저장 시 즉시 비운다. */
    private static final long CACHE_TTL_MS = 60_000L;

    private final CommonDAO commonDAO;

    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    private record Snapshot(Map<String, String> values, long loadedAt) {
    }

    // ===== 화면(관리자) =====

    /** 정의 + 현재 값 전체. 프론트가 group_cd로 묶어 순서대로 그린다. */
    public List<ConfigItemVO> selectList(ConfigItemVO vo) {
        return commonDAO.selectList("configItemDAO.selectList", vo);
    }

    /**
     * 공개 항목만(비로그인 포함). 사용자 화면이 푸터 문구·메인 설정 같은 값을 읽는 데 쓴다.
     * public_yn='Y'로 명시한 것만 나가므로, 나중에 내부용 설정을 추가해도 자동으로 새어나가지 않는다.
     */
    public List<ConfigItemVO> selectListPublic() {
        return commonDAO.selectList("configItemDAO.selectListPublic", new ConfigItemVO());
    }

    /**
     * 값 일괄 저장. 화면이 보낸 키만 갱신한다.
     *
     * <p>정의가 없는(또는 미사용) 키는 <b>거부</b>한다 — 조용히 무시하면 화면에서 저장한 것처럼
     * 보이지만 아무 일도 일어나지 않아 원인 찾기가 어렵다.
     */
    @Transactional
    public void updateValues(List<ConfigItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ConfigItemVO item : items) {
            int updated = commonDAO.update("configItemDAO.updateValue", item);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        Messages.get("error.configitem.unknownKey", item.getConfigKey()));
            }
        }
        evict();
    }

    // ===== 값 읽기(다른 도메인이 사용) =====

    /** 문자열 값. 없으면 기본값. */
    public String get(String key, String defaultValue) {
        String value = snapshot().values().get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 숫자 값. 없거나 숫자가 아니면 기본값(경고 로그). */
    public int getInt(String key, int defaultValue) {
        String value = snapshot().values().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[ConfigItem] 숫자로 읽을 수 없는 설정값 — key={}, value={} → 기본값 {} 사용", key, value, defaultValue);
            return defaultValue;
        }
    }

    /** Y/N 값. 'Y'(대소문자 무시)만 true, 그 밖은 기본값이 아니라 false로 본다. */
    public boolean getBool(String key, boolean defaultValue) {
        String value = snapshot().values().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "Y".equalsIgnoreCase(value.trim());
    }

    /** 캐시 무효화 — DB에서 값을 직접 바꿨을 때 즉시 반영시키는 용도. */
    public void evict() {
        cache.set(null);
    }

    private Snapshot snapshot() {
        Snapshot cur = cache.get();
        if (cur != null && System.currentTimeMillis() - cur.loadedAt() < CACHE_TTL_MS) {
            return cur;
        }
        List<ConfigItemVO> rows = commonDAO.selectList("configItemDAO.selectAllValues", new ConfigItemVO());
        Map<String, String> values = new HashMap<>();
        for (ConfigItemVO row : rows) {
            values.put(row.getConfigKey(), row.getValue());
        }
        Snapshot fresh = new Snapshot(values, System.currentTimeMillis());
        cache.set(fresh);
        return fresh;
    }
}
