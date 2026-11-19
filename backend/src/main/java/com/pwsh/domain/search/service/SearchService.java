package com.pwsh.domain.search.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.searchstat.service.SearchStatService;
import com.pwsh.domain.searchsynonym.service.SearchSynonymService;
import com.pwsh.global.security.SecurityUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 통합 검색(단일 @Service). 취미·모집·게시글·안내페이지를 키워드로 한 번에 조회한다.
 *
 * <p>★ <b>검색은 권한 우회 통로가 되기 쉽다.</b> 목록·상세는 {@code GenAccessGuard}가 막아도,
 * 검색이 제목·본문 조각을 그대로 보여주면 열람 불가 콘텐츠가 새어 나간다. 그래서 판정 기준
 * (회원·관리자 여부)을 <b>서비스가 직접 세팅</b>하고 매퍼가 대상을 먼저 좁힌다 —
 * 클라이언트가 보낸 값을 쓰면 남의 권한으로 검색할 수 있다.
 *
 * <p>유형마다 최대 {@link #PREVIEW_SIZE}건만 내려주되 <b>총건수는 따로 센다</b>.
 * 목록 길이를 건수로 쓰면 결과가 23건이어도 화면에 "10"으로 찍혀 사용자를 속인다.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    /** 유형별로 보여주는 최대 건수 */
    private static final int PREVIEW_SIZE = 10;

    private final CommonDAO commonDAO;
    private final SearchStatService searchStatService;
    private final SearchSynonymService searchSynonymService;

    /**
     * 통합 검색. 결과가 네 유형 모두 0건이면 오타 교정 제안({@code suggest})을 함께 싣는다(CMS 1.2.39, 사용자 결정: 0건일 때 제안만).
     */
    public Map<String, Object> searchAll(String keyword) {
        Map<String, Object> p = prepare(keyword);
        Map<String, Object> result = new LinkedHashMap<>();
        if (((String) p.get("keyword")).isEmpty()) {
            // 빈 검색어로 전체 스캔을 돌리지 않는다
            for (String key : List.of("hobbies", "recruits", "posts", "pages")) {
                result.put(key, List.of());
            }
            for (String key : List.of("hobbyCount", "recruitCount", "postCount", "pageCount")) {
                result.put(key, 0);
            }
            return result;
        }
        result.put("hobbies", commonDAO.selectList("searchDAO.searchHobbies", p));
        result.put("hobbyCount", (int) commonDAO.selectOne("searchDAO.searchHobbiesCount", p));
        result.put("recruits", commonDAO.selectList("searchDAO.searchRecruits", p));
        result.put("recruitCount", (int) commonDAO.selectOne("searchDAO.searchRecruitsCount", p));
        result.put("posts", commonDAO.selectList("searchDAO.searchPosts", p));
        result.put("postCount", (int) commonDAO.selectOne("searchDAO.searchPostsCount", p));
        result.put("pages", commonDAO.selectList("searchDAO.searchPages", p));
        result.put("pageCount", (int) commonDAO.selectOne("searchDAO.searchPagesCount", p));
        // 검색 통계(CMS 1.2.38) — 날짜·검색어별 횟수만(누가 검색했는지는 남기지 않는다). 결과 수는 네 유형 합
        int total = (int) result.get("hobbyCount") + (int) result.get("recruitCount")
                + (int) result.get("postCount") + (int) result.get("pageCount");
        searchStatService.record((String) p.get("keyword"), total);
        // 오타 교정(CMS 1.2.39) — 결과가 있으면 묻지 않는다
        result.put("suggest", total == 0 ? searchStatService.correct((String) p.get("keyword")) : null);
        return result;
    }

    /** 인기 검색어(공개, CMS 1.2.38) — 집계·금칙어·숨김 규칙은 SearchStatService가 갖는다 */
    public List<String> popular() {
        return searchStatService.popular();
    }

    /** 자동완성(공개, CMS 1.2.39) — 자주 검색되고 결과가 있던 말에서(인기 검색어와 같은 거름) */
    public List<String> autocomplete(String keyword) {
        return searchStatService.autocomplete(keyword);
    }

    /**
     * 검색어 정리 + 동의어 확장 + 판정 기준 주입. 모든 진입점이 반드시 거친다.
     * 동의어(CMS 1.2.39): 검색어 전체가 묶음의 한 말과 같으면 입력한 말을 먼저, 묶음의 나머지 말을 뒤에 둔다 —
     * 매퍼는 원문 목록(keywords)·이스케이프 목록(keywordLikes)을 foreach로 OR 묶는다.
     */
    private Map<String, Object> prepare(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        List<String> terms = new ArrayList<>();
        if (!trimmed.isEmpty()) {
            terms.add(trimmed);
            String self = SearchStatService.normalize(trimmed);
            for (String w : searchSynonymService.expand(trimmed)) {
                if (!w.equals(self)) {
                    terms.add(w);
                }
            }
        }
        Map<String, Object> p = new HashMap<>();
        p.put("keyword", trimmed);
        p.put("keywords", terms);
        p.put("keywordLikes", terms.stream().map(this::escapeLike).toList());
        p.put("previewSize", PREVIEW_SIZE);
        p.put("adminYn", SecurityUtil.isAdmin() ? "Y" : "N");
        String memberId = SecurityUtil.getCurrentMemberId();
        p.put("memberId", (memberId == null || "system".equals(memberId)) ? null : memberId);
        return p;
    }

    /**
     * LIKE 패턴 특수문자 이스케이프. 사용자가 넣은 {@code % _ \}를 리터럴로 취급한다 —
     * 미처리 시 {@code %} 한 글자로 <b>전체 조회</b>가 되어 권한 밖 제목이 몽땅 나온다.
     * 매퍼는 ESCAPE '\' 를 명시한다.
     */
    private String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
