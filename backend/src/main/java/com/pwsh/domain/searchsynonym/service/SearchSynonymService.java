package com.pwsh.domain.searchsynonym.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.domain.searchstat.service.SearchStatService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색 동의어 묶음(CMS 1.2.39, 단일 @Service).
 *
 * <p>사용자 결정: 검색어 <b>전체</b>가 묶음의 한 말과 같을 때만 묶음 전체로 찾는다({@link #expand}) — 다른 말이 붙은 검색어는 그대로.
 * 말은 검색 통계와 같은 규칙({@link SearchStatService#normalize})으로 정리해 비교·저장한다.
 * ★ 한 말은 한 묶음에만 — 두 묶음에 있으면 같은 검색어가 어느 묶음으로 넓혀질지 정할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class SearchSynonymService {

    private static final int MIN_WORDS = 2;
    private static final int MAX_WORDS = 20;
    private static final int MAX_WORD_LEN = 50;

    private final CommonDAO commonDAO;

    public List<SearchSynonymVO> selectList(SearchSynonymVO vo) {
        prepareFilter(vo);
        return commonDAO.selectList("searchSynonymDAO.selectList", vo);
    }

    public int selectListTotalCount(SearchSynonymVO vo) {
        prepareFilter(vo);
        return commonDAO.selectOne("searchSynonymDAO.selectListTotalCount", vo);
    }

    public SearchSynonymVO selectView(SearchSynonymVO vo) {
        return commonDAO.selectOne("searchSynonymDAO.selectView", vo);
    }

    @Transactional
    public void insert(SearchSynonymVO vo) {
        vo.setWords(String.join(",", validate(vo.getWords(), null)));
        commonDAO.insert("searchSynonymDAO.insert", vo);
    }

    @Transactional
    public void update(SearchSynonymVO vo) {
        vo.setWords(String.join(",", validate(vo.getWords(), vo.getRowId())));
        commonDAO.update("searchSynonymDAO.update", vo);
    }

    public void delete(SearchSynonymVO vo) {
        commonDAO.update("searchSynonymDAO.delete", vo);
    }

    /**
     * 검색어가 어느 묶음의 한 말과 (정리한 뒤) 같으면 그 묶음의 말 전부, 아니면 빈 목록.
     * 호출자(통합검색)는 입력한 말을 먼저 두고 나머지를 덧붙여 찾는다.
     */
    public List<String> expand(String keyword) {
        String k = SearchStatService.normalize(keyword);
        if (k.isEmpty()) {
            return List.of();
        }
        for (SearchSynonymVO g : commonDAO.<SearchSynonymVO>selectList("searchSynonymDAO.selectAll", new SearchSynonymVO())) {
            List<String> words = split(g.getWords());
            if (words.contains(k)) {
                return words;
            }
        }
        return List.of();
    }

    /** 정리·중복 제거 → 2~20개·말마다 50자까지·다른 묶음과 겹치지 않음(자기 묶음은 제외) */
    private List<String> validate(String raw, String selfRowId) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : (raw == null ? "" : raw).split("[,\\r\\n]+")) {
            String n = SearchStatService.normalize(w);
            if (!n.isEmpty()) {
                words.add(n);
            }
        }
        if (words.size() < MIN_WORDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.searchSynonym.tooFew", MIN_WORDS));
        }
        if (words.size() > MAX_WORDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.searchSynonym.tooMany", MAX_WORDS));
        }
        for (String w : words) {
            if (w.length() > MAX_WORD_LEN) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.searchSynonym.tooLong", MAX_WORD_LEN));
            }
        }
        for (SearchSynonymVO g : commonDAO.<SearchSynonymVO>selectList("searchSynonymDAO.selectAll", new SearchSynonymVO())) {
            if (g.getRowId().equals(selfRowId)) {
                continue;
            }
            for (String other : split(g.getWords())) {
                if (words.contains(other)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.searchSynonym.duplicate", other));
                }
            }
        }
        return List.copyOf(words);
    }

    private static List<String> split(String stored) {
        return stored == null ? List.of() : Arrays.stream(stored.split(",")).filter(s -> !s.isEmpty()).toList();
    }

    private void prepareFilter(SearchSynonymVO vo) {
        String f = vo.getFilterKeyword() == null ? "" : SearchStatService.normalize(vo.getFilterKeyword());
        vo.setFilterKeyword(f);
        vo.setKeywordLike(f.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
    }
}
