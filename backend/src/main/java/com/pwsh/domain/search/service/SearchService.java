package com.pwsh.domain.search.service;

import com.pwsh.common.CommonDAO;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 통합 검색(단일 @Service). 취미·모집·게시글을 키워드로 한 번에 조회(각 최대 10건).
 * 게시글은 취미(공개) 게시판의 비밀글 아닌 원글만 — 비공개/비밀 콘텐츠 누출 방지.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final CommonDAO commonDAO;

    public Map<String, Object> searchAll(String keyword) {
        Map<String, Object> p = new HashMap<>();
        p.put("keyword", escapeLike(keyword));
        Map<String, Object> result = new HashMap<>();
        result.put("hobbies", commonDAO.selectList("searchDAO.searchHobbies", p));
        result.put("recruits", commonDAO.selectList("searchDAO.searchRecruits", p));
        result.put("posts", commonDAO.selectList("searchDAO.searchPosts", p));
        return result;
    }

    /**
     * LIKE 패턴 특수문자 이스케이프. 사용자가 넣은 % _ \ 를 리터럴로 취급한다
     * (미처리 시 '%' 한 글자로 전체 조회되는 등 검색 결과가 왜곡됨). 매퍼는 ESCAPE '\' 를 명시.
     */
    private String escapeLike(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
