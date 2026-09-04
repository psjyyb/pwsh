package com.pwsh.domain.banword.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 금칙어 업무 로직. 컨트롤러는 매핑만, 로직은 여기(단일 @Service).
 *
 * <p>검사는 {@link #assertClean}로 게시글·댓글 저장 경로에서 호출한다.
 * 등록 건수가 많지 않아 전건을 읽어 대조한다(단어별 SQL LIKE를 돌리면 쿼리가 건수만큼 늘어난다).
 */
@Service
@RequiredArgsConstructor
public class BanwordService {

    private final CommonDAO commonDAO;

    public List<BanwordVO> selectList(BanwordVO vo) {
        return commonDAO.selectList("banwordDAO.selectList", vo);
    }

    public int selectListTotalCount(BanwordVO vo) {
        return commonDAO.selectOne("banwordDAO.selectListTotalCount", vo);
    }

    public BanwordVO selectView(BanwordVO vo) {
        return commonDAO.selectOne("banwordDAO.selectView", vo);
    }

    public void insert(BanwordVO vo) {
        assertNotDuplicated(vo);
        commonDAO.insert("banwordDAO.insert", vo);
    }

    public void update(BanwordVO vo) {
        assertNotDuplicated(vo);
        commonDAO.update("banwordDAO.update", vo);
    }

    public void delete(BanwordVO vo) {
        commonDAO.delete("banwordDAO.delete", vo);
    }

    private void assertNotDuplicated(BanwordVO vo) {
        Integer cnt = commonDAO.selectOne("banwordDAO.selectCountByWord", vo);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 금칙어입니다.");
        }
    }

    /**
     * 금칙어 검사 — 하나라도 포함되면 400으로 막는다. 대소문자·공백은 무시한다
     * ("무 료 광 고" 처럼 띄어쓰기로 피하는 회피를 함께 막기 위함).
     *
     * @param texts 검사 대상(제목·본문 등). null은 건너뛴다.
     */
    public void assertClean(String... texts) {
        List<String> words = commonDAO.selectList("banwordDAO.selectWords", new BanwordVO());
        if (words.isEmpty()) {
            return;
        }
        for (String text : texts) {
            String target = normalize(text);
            if (target.isEmpty()) {
                continue;
            }
            Optional<String> hit = words.stream()
                    .map(BanwordService::normalize)
                    .filter(w -> !w.isEmpty() && target.contains(w))
                    .findFirst();
            if (hit.isPresent()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "사용할 수 없는 단어가 포함되어 있습니다.");
            }
        }
    }

    /** 대소문자·공백 제거(회피 방지). */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }
}
