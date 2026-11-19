package com.pwsh.domain.searchsynonym.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 검색 동의어 묶음 VO (search_synonym). PK(synonym_id)는 BaseVO.rowId. */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchSynonymVO extends BaseVO {

    /** 묶음의 말들. 화면은 쉼표·줄바꿈으로 나눠 보내고, 서버가 정리해 쉼표로 이어 저장한다 */
    private String words;
    /** 목록 찾기용 — LIKE 특수문자 이스케이프본(원문은 filterKeyword). 서비스가 채운다 */
    private String keywordLike;
}
