package com.pwsh.domain.accessip.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.common.util.IpMatcher;
import com.pwsh.domain.config.service.ConfigVO;
import com.pwsh.global.web.ClientIpHolder;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 관리자 접속 허용 IP 업무 로직. 컨트롤러는 매핑만, 로직은 여기(단일 @Service).
 *
 * <p>{@link #isAllowed}는 모든 관리자 요청마다 불리므로 DB를 매번 읽지 않고
 * 짧은 TTL 스냅샷을 둔다. 등록·수정·삭제 시에는 스냅샷을 즉시 비워
 * 화면에서 IP를 추가한 직후 바로 반영되게 한다.
 */
@Service
@RequiredArgsConstructor
public class AccessIpService {

    /** 설정 변경이 반영되기까지 최대 지연(ms). 짧게 잡아도 요청당 DB 조회는 거의 사라진다. */
    private static final long CACHE_TTL_MS = 30_000L;

    // 환경설정을 ConfigService가 아니라 DAO로 직접 읽는다 —
    // ConfigService가 자기 잠금 방지 검사를 위해 이 서비스를 주입받으므로, 반대로 참조하면 순환 의존이 된다.
    private final CommonDAO commonDAO;

    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    /** 허용 IP 목록 + 제한 사용여부를 한 번에 담는다(둘을 따로 캐시하면 서로 어긋난 조합이 보일 수 있다). */
    private record Snapshot(boolean enabled, List<String> ips, long loadedAt) {
    }

    public List<AccessIpVO> selectList(AccessIpVO vo) {
        return commonDAO.selectList("accessIpDAO.selectList", vo);
    }

    public int selectListTotalCount(AccessIpVO vo) {
        return commonDAO.selectOne("accessIpDAO.selectListTotalCount", vo);
    }

    public AccessIpVO selectView(AccessIpVO vo) {
        return commonDAO.selectOne("accessIpDAO.selectView", vo);
    }

    public void insert(AccessIpVO vo) {
        assertValidIp(vo.getIp());
        assertNotDuplicated(vo);
        commonDAO.insert("accessIpDAO.insert", vo);
        evict();
    }

    public void update(AccessIpVO vo) {
        assertValidIp(vo.getIp());
        assertNotDuplicated(vo);
        commonDAO.update("accessIpDAO.update", vo);
        evict();
    }

    public void delete(AccessIpVO vo) {
        assertDeletableWithoutLockout(vo);
        commonDAO.delete("accessIpDAO.delete", vo);
        evict();
    }

    /**
     * 지운 뒤에도 내 IP가 허용되는지 미리 계산한다. 지우고 나서 검사하면
     * 이 서비스에는 트랜잭션 경계가 없어 되돌릴 수 없다(BanwordService와 동일한 무-트랜잭션 CRUD).
     */
    private void assertDeletableWithoutLockout(AccessIpVO vo) {
        if (!isEnforced()) {
            return; // 제한이 동작하지 않는 상태에서는 잠길 일이 없다
        }
        List<String> remain = commonDAO.selectList("accessIpDAO.selectIpsExcept", vo);
        String myIp = ClientIpHolder.get();
        if (remain.isEmpty()) {
            return; // 전부 지우면 제한 자체가 풀리므로(isAllowed 참조) 잠기지 않는다
        }
        if (myIp == null || remain.stream().noneMatch(rule -> IpMatcher.matches(myIp, rule))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.accessip.deleteSelfLockout", myIp));
        }
    }

    /**
     * 환경설정에서 접속 IP 제한을 켜기 직전에 호출 — 지금 접속 중인 IP가 허용되지 않으면 막는다.
     * 이 검사가 없으면 관리자가 자기 IP를 등록하지 않고 제한을 켜는 순간 스스로 잠기고,
     * 복구가 DB SQL로만 가능해진다.
     */
    public void assertNotSelfLockout() {
        List<String> ips = commonDAO.selectList("accessIpDAO.selectIps", new AccessIpVO());
        if (ips.isEmpty()) {
            return; // 목록이 비면 제한이 동작하지 않으므로(isAllowed 참조) 잠기지 않는다
        }
        String myIp = ClientIpHolder.get();
        if (myIp == null || ips.stream().noneMatch(rule -> IpMatcher.matches(myIp, rule))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.accessip.selfLockout", myIp));
        }
    }

    /**
     * 관리자 접속 허용 여부.
     *
     * <p>다음 중 하나라도 해당하면 <b>제한하지 않는다</b>(true).
     * <ul>
     *   <li>환경설정의 접속 IP 제한이 꺼져 있음</li>
     *   <li>등록된 허용 IP가 하나도 없음 — 목록이 비었는데 막으면 아무도 관리자 화면에
     *       들어갈 수 없어 복구가 SQL로만 가능해진다(잠금 방지)</li>
     * </ul>
     *
     * @param clientIp 요청자 IP. null/공백이면 판별 불가로 보고 거부한다.
     */
    public boolean isAllowed(String clientIp) {
        Snapshot snap = snapshot();
        if (!snap.enabled() || snap.ips().isEmpty()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        return snap.ips().stream().anyMatch(rule -> IpMatcher.matches(clientIp, rule));
    }

    /** 제한이 실제로 걸려 있는지(화면 안내용) — 켜져 있고 목록도 있는 상태. */
    public boolean isEnforced() {
        Snapshot snap = snapshot();
        return snap.enabled() && !snap.ips().isEmpty();
    }

    /** 캐시 무효화 — 환경설정에서 제한 사용여부를 바꿨을 때도 호출해야 한다. */
    public void evict() {
        cache.set(null);
    }

    private Snapshot snapshot() {
        Snapshot cur = cache.get();
        if (cur != null && System.currentTimeMillis() - cur.loadedAt() < CACHE_TTL_MS) {
            return cur;
        }
        ConfigVO config = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        boolean enabled = config != null && "Y".equals(config.getAccIpYn());
        List<String> ips = enabled
                ? commonDAO.selectList("accessIpDAO.selectIps", new AccessIpVO())
                : List.of();
        Snapshot fresh = new Snapshot(enabled, ips, System.currentTimeMillis());
        cache.set(fresh);
        return fresh;
    }

    /** 같은 IP를 두 번 등록하면 목록만 지저분해지고 삭제 시 혼란을 준다(어느 쪽을 지워야 할지 모른다). */
    private void assertNotDuplicated(AccessIpVO vo) {
        Integer cnt = commonDAO.selectOne("accessIpDAO.selectCountByIp", vo);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, Messages.get("error.accessip.duplicate"));
        }
    }

    /** 형식 검증 — 저장 시점에 막지 않으면 "등록은 됐는데 매칭이 안 되는" IP가 남는다. */
    private static void assertValidIp(String ip) {
        if (!IpMatcher.isValidRule(ip)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    Messages.get("error.accessip.invalidFormat"));
        }
    }
}
