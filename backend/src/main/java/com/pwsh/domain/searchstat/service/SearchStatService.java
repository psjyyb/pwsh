package com.pwsh.domain.searchstat.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.banword.service.BanwordService;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색 통계 · 인기 검색어(CMS 1.2.38, 단일 @Service).
 *
 * <p>통합검색 1회마다 날짜·검색어별 횟수만 쌓는다({@link #record}) — 누가 검색했는지(회원·IP)는 남기지 않는다(사용자 결정).
 * 인기 검색어는 그 집계에서 뽑되, 한두 번 들어온 말·결과가 한 번도 없던 말·금칙어가 든 말·관리자가 숨긴 말은 뺀다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchStatService {

    /** 집계하는 검색어 최대 길이(표 컬럼 길이) — 넘는 검색은 세지 않는다 */
    public static final int MAX_KEYWORD = 100;
    /** 인기 검색어: 최근 며칠(오늘 포함) */
    static final int POPULAR_DAYS = 7;
    /** 인기 검색어: 보여 줄 개수 */
    static final int POPULAR_SIZE = 10;
    /** 인기 검색어: 기간 합계 최소 횟수 — 한 사람이 한 번 넣은 이름·번호가 공개 목록에 오르지 않게 */
    static final int POPULAR_MIN_COUNT = 2;
    /** 금칙어를 거르고도 POPULAR_SIZE를 채우도록 넉넉히 뽑는 후보 수 */
    private static final int POPULAR_CANDIDATES = 50;
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 365;

    private final CommonDAO commonDAO;
    private final BanwordService banwordService;

    /** 검색어 정리 — 앞뒤 공백 제거·연속 공백 하나·소문자. 집계·숨김·인기 검색어가 모두 이 형태로 비교한다. */
    public static String normalize(String keyword) {
        return keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 통합검색 1회 기록. 빈 검색어·100자 초과는 세지 않는다.
     * ★ 기록 실패가 검색을 막지 않는다 — 통계는 부수 기능이다(경고 로그만).
     *
     * @param resultCnt 게시글+페이지 결과 건수(0이면 "결과 없음"도 1 더한다)
     */
    public void record(String keyword, int resultCnt) {
        String k = normalize(keyword);
        if (k.isEmpty() || k.length() > MAX_KEYWORD) {
            return;
        }
        try {
            SearchStatVO vo = new SearchStatVO();
            vo.setKeyword(k);
            vo.setZeroCnt(resultCnt == 0 ? "1" : "0");
            commonDAO.insert("searchStatDAO.upsert", vo);
        } catch (RuntimeException e) {
            log.warn("[SearchStat] 검색 기록 실패: {}", e.getMessage());
        }
    }

    /** 인기 검색어(공개) — 최근 7일 2회 이상·결과가 있던 말, 금칙어·숨긴 말 제외, 많은 순 10개. 횟수는 싣지 않는다. */
    public List<String> popular() {
        SearchStatVO vo = new SearchStatVO();
        vo.setDays(String.valueOf(POPULAR_DAYS));
        vo.setMinCount(String.valueOf(POPULAR_MIN_COUNT));
        vo.setLimitCnt(String.valueOf(POPULAR_CANDIDATES));
        List<String> candidates = commonDAO.selectList("searchStatDAO.selectPopular", vo);
        Predicate<String> banned = banwordService.matcher();
        return candidates.stream().filter(banned.negate()).limit(POPULAR_SIZE).toList();
    }

    /** 관리 화면: 기간 안 검색어 순위(금칙어 포함 여부는 서버가 계산해 싣는다) */
    public List<SearchStatVO> selectList(SearchStatVO vo) {
        prepare(vo);
        List<SearchStatVO> list = commonDAO.selectList("searchStatDAO.selectList", vo);
        Predicate<String> banned = banwordService.matcher();
        list.forEach(r -> r.setBanYn(banned.test(r.getKeyword()) ? "Y" : "N"));
        return list;
    }

    public int selectListTotalCount(SearchStatVO vo) {
        prepare(vo);
        return commonDAO.selectOne("searchStatDAO.selectListTotalCount", vo);
    }

    /** 일별 합계(오늘부터 거꾸로, 빈 날 0) */
    public List<SearchStatVO> selectDaily(SearchStatVO vo) {
        prepare(vo);
        return commonDAO.selectList("searchStatDAO.selectDaily", vo);
    }

    /** 인기 검색어에서 숨김/해제 — 정리한 검색어 기준. 이미 숨긴 말을 또 숨기거나 숨기지 않은 말을 풀어도 오류가 아니다. */
    @Transactional
    public void hide(SearchStatVO vo) {
        String k = normalize(vo.getKeyword());
        if (k.isEmpty() || k.length() > MAX_KEYWORD) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.validate.required", "검색어"));
        }
        vo.setKeyword(k);
        if ("Y".equals(vo.getHideYn())) {
            Integer cnt = commonDAO.selectOne("searchStatDAO.selectHideCount", vo);
            if (cnt == null || cnt == 0) {
                commonDAO.insert("searchStatDAO.insertHide", vo);
            }
        } else {
            commonDAO.update("searchStatDAO.unhide", vo);
        }
    }

    /** 기간(1~365일, 기본 7)과 검색 조건 정리 — 화면 값을 그대로 쓰면 ::integer 캐스트가 500으로 샌다 */
    private void prepare(SearchStatVO vo) {
        int days = DEFAULT_DAYS;
        try {
            days = Integer.parseInt(vo.getDays() == null ? "" : vo.getDays().trim());
        } catch (NumberFormatException e) {
            days = DEFAULT_DAYS;
        }
        vo.setDays(String.valueOf(Math.max(1, Math.min(MAX_DAYS, days))));
        String f = vo.getFilterKeyword() == null ? "" : normalize(vo.getFilterKeyword());
        vo.setFilterKeyword(f);
        vo.setKeywordLike(f.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
    }
}
