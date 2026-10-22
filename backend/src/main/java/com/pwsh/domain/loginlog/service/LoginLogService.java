package com.pwsh.domain.loginlog.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.eventlog.service.EventLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로그인 이력 — 기록(AuthService가 부른다)과 관리자 조회. append-only라 수정·삭제가 없다.
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    public static final String SUCCESS = "LOGIN01";
    public static final String FAIL_PASSWORD = "LOGIN02";
    public static final String FAIL_UNKNOWN_ID = "LOGIN03";
    public static final String FAIL_LOCKED = "LOGIN04";
    public static final String FAIL_SUSPENDED = "LOGIN05";
    public static final String FAIL_DORMANT = "LOGIN06";
    public static final String FAIL_IP = "LOGIN07";
    public static final String FAIL_ETC = "LOGIN09";

    /** login_log.login_id 길이. 입력값이 길어도 기록 때문에 로그인 응답이 500으로 바뀌면 안 된다 */
    private static final int LOGIN_ID_MAX = 50;
    private static final int MESSAGE_MAX = 500;

    private final CommonDAO commonDAO;

    public List<LoginLogVO> selectList(LoginLogVO vo) {
        escapeKeyword(vo);
        return commonDAO.selectList("loginLogDAO.selectList", vo);
    }

    public int selectListTotalCount(LoginLogVO vo) {
        escapeKeyword(vo);
        return commonDAO.selectOne("loginLogDAO.selectListTotalCount", vo);
    }

    /**
     * 시도 1건 기록.
     *
     * <p>★ 별도 트랜잭션(REQUIRES_NEW)이다 — 실패 기록은 예외가 던져지는 길목에서 남기므로, 호출하는 쪽이
     * 트랜잭션 안이면 그 롤백에 기록까지 같이 사라진다. 지금 로그인은 트랜잭션이 아니지만, 나중에 붙여도
     * 실패 기록이 없어지지 않게 미리 떼어 둔다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String loginId, String resultCd, String failMessage) {
        LoginLogVO vo = new LoginLogVO();
        vo.setLoginId(cut(loginId == null ? "" : loginId, LOGIN_ID_MAX));
        vo.setResultCd(resultCd);
        vo.setFailMessage(failMessage == null ? null : cut(failMessage, MESSAGE_MAX));
        String ua = currentUserAgent();
        vo.setUserAgent(ua);
        vo.setDeviceType(EventLogService.parseDevice(ua));
        commonDAO.insert("loginLogDAO.insert", vo);
    }

    /**
     * 검색어의 LIKE 특수문자를 글자 그대로 — 안 하면 '%' 한 글자가 전체 조회가 된다(매퍼 ESCAPE '\').
     *
     * <p>★ 원래 검색어(filterKeyword)는 건드리지 않고 이스케이프본을 따로 둔다. 컨트롤러가 같은 VO로
     * 건수·목록을 차례로 부르므로, 원본을 덮어쓰면 두 번째 호출에서 '\'가 겹쳐 목록만 비어 나온다.
     */
    private void escapeKeyword(LoginLogVO vo) {
        String k = vo.getFilterKeyword();
        vo.setKeywordLike(k == null || k.isBlank() ? null
                : k.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String currentUserAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr) {
            return attr.getRequest().getHeader("User-Agent");
        }
        return null;
    }
}
