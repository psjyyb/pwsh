package com.pwsh.domain.searchstat.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.banword.service.BanwordService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    /** 자동완성·오타 교정이 보는 기간(오늘 포함) — 인기 검색어(7일)보다 넓게 잡아 드물게 찾는 말도 돕는다 */
    static final int ASSIST_DAYS = 30;
    static final int AUTOCOMPLETE_SIZE = 8;
    private static final int AUTOCOMPLETE_CANDIDATES = 40;
    /** 오타 교정 후보 수(많이 검색된 순) — 매 0건 검색마다 이만큼 편집 거리를 계산한다 */
    private static final int CORRECT_CANDIDATES = 300;
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 365;

    private final CommonDAO commonDAO;
    private final BanwordService banwordService;

    /** 집계 행 보존기간(일). 0이면 지우지 않는다(CMS 1.2.40, 사용자 결정: 1년 — 통계 화면의 최대 기간과 같다) */
    @Value("${search.stat.retention-days:365}")
    private int retentionDays;

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

    /**
     * 보존기간(search.stat.retention-days) 지난 집계 행 삭제(CMS 1.2.40). 배치 작업 관리(BatchService, 작업 코드 SEARCH_STAT_PURGE)가 부른다.
     * 경계는 통계 화면과 같은 "오늘 포함 N일"이다 — 1년(365)이면 화면의 '최근 1년'에 보이는 행은 남고 그 밖의 행만 지운다.
     * 숨긴 말(search_keyword_hide)은 관리자 설정이라 지우지 않는다. 누가 검색했는지가 없는 집계라 법정 보존 대상이 아니다.
     *
     * @return 지운 행 수
     */
    public int purgeExpired() {
        if (retentionDays <= 0) {
            return 0;
        }
        int deleted = commonDAO.delete("searchStatDAO.deleteExpired", Map.of("days", String.valueOf(retentionDays)));
        if (deleted > 0) {
            log.info("검색 통계 정리: {}건 삭제(보존 {}일)", deleted, retentionDays);
        }
        return deleted;
    }

    /** 인기 검색어(공개) — 최근 7일 2회 이상·결과가 있던 말, 금칙어·숨긴 말 제외, 많은 순 10개. 횟수는 싣지 않는다. */
    public List<String> popular() {
        return frequent(POPULAR_DAYS, POPULAR_CANDIDATES, null).stream().limit(POPULAR_SIZE).toList();
    }

    /**
     * 자동완성(공개, CMS 1.2.39) — 최근 30일 2회 이상·결과가 있던 말 중 입력이 든 말. 앞이 맞는 말 먼저(각각 많은 순), 8개.
     * 인기 검색어와 같은 거름(최소 횟수·결과 있던 말·금칙어·숨김)을 탄다 — 사용자 결정: 자주 검색된 말에서만
     * (게시글 제목에서 뽑으면 권한마다 결과가 달라 비공개·비밀글 제목이 샐 수 있다).
     */
    public List<String> autocomplete(String input) {
        String k = normalize(input);
        if (k.isEmpty() || k.length() > MAX_KEYWORD) {
            return List.of();
        }
        List<String> hits = frequent(ASSIST_DAYS, AUTOCOMPLETE_CANDIDATES, escapeLike(k));
        return java.util.stream.Stream.concat(
                        hits.stream().filter(h -> h.startsWith(k)),
                        hits.stream().filter(h -> !h.startsWith(k)))
                .limit(AUTOCOMPLETE_SIZE)
                .toList();
    }

    /**
     * 오타 교정 제안(CMS 1.2.39, 사용자 결정: 결과 0건일 때 제안만) — 자주 검색되고 결과가 있던 말 중 자모 단위로 가장 가까운 말.
     * 차이가 길이에 비해 크면(짧으면 1, 보통 2, 길면 3 자모까지) 제안하지 않는다. 같은 거리면 많이 검색된 말.
     * 한글을 초·중·종성으로 풀어 비교한다 — 글자 단위로 보면 '레'와 '래'처럼 모음 하나 틀린 것도 통째로 다른 글자가 된다.
     *
     * @return 제안할 말, 없으면 null
     */
    public String correct(String keyword) {
        String k = normalize(keyword);
        if (k.isEmpty() || k.length() > MAX_KEYWORD) {
            return null;
        }
        String target = jamo(k);
        int allowed = target.length() <= 6 ? 1 : target.length() <= 15 ? 2 : 3;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : frequent(ASSIST_DAYS, CORRECT_CANDIDATES, null)) { // 많은 순 — 같은 거리면 앞의 것이 남는다
            if (candidate.equals(k)) {
                continue;
            }
            int d = distance(target, jamo(candidate), allowed);
            if (d <= allowed && d < bestDistance) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * 자주 검색된 말 — 기간 합계 2회 이상·결과가 한 번이라도 있던 말, 숨긴 말 제외(SQL), 금칙어가 든 말 제외(여기), 많은 순.
     * 인기 검색어·자동완성·오타 교정이 같은 거름을 쓴다.
     */
    private List<String> frequent(int days, int limit, String keywordLike) {
        SearchStatVO vo = new SearchStatVO();
        vo.setDays(String.valueOf(days));
        vo.setMinCount(String.valueOf(POPULAR_MIN_COUNT));
        vo.setLimitCnt(String.valueOf(limit));
        vo.setKeywordLike(keywordLike);
        List<String> candidates = commonDAO.selectList("searchStatDAO.selectFrequent", vo);
        Predicate<String> banned = banwordService.matcher();
        return candidates.stream().filter(banned.negate()).toList();
    }

    /** 한글 음절을 초·중·종성 자모로 푼다(그 밖의 글자는 그대로) — 오타 거리 계산용 */
    static String jamo(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 3);
        for (char c : s.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int i = c - 0xAC00;
                sb.append((char) (0x1100 + i / 588));            // 초성
                sb.append((char) (0x1161 + (i % 588) / 28));     // 중성
                int jong = i % 28;
                if (jong > 0) {
                    sb.append((char) (0x11A7 + jong));           // 종성
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 편집 거리(삽입·삭제·바꾸기 1씩). 길이 차가 한도를 넘으면 계산하지 않고 한도+1 */
    static int distance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
        vo.setKeywordLike(escapeLike(f));
    }
}
