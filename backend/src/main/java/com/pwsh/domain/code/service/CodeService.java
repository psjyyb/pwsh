package com.pwsh.domain.code.service;

import com.pwsh.common.CommonDAO;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통코드 업무 로직. 컨트롤러는 매핑만 하고 로직·트랜잭션은 여기서 담당.
 * (단일 @Service — interface/Impl 분리 없음. 모든 도메인 공통 방식.)
 */
@Service
@RequiredArgsConstructor
public class CodeService {

    private final CommonDAO commonDAO;

    public List<CodeVO> selectList(CodeVO vo) {
        return commonDAO.selectList("codeDAO.selectList", vo);
    }

    public int selectListTotCnt(CodeVO vo) {
        return commonDAO.selectOne("codeDAO.selectListTotCnt", vo);
    }

    public CodeVO selectView(CodeVO vo) {
        return commonDAO.selectOne("codeDAO.selectView", vo);
    }

    /** 계층 트리용 전체 조회 (프론트에서 p_code_id로 중첩) */
    public List<CodeVO> selectTree(CodeVO vo) {
        return commonDAO.selectList("codeDAO.selectTree", vo);
    }

    /** 콤보/셀렉트용: pCodeId 하위의 사용중 코드 정렬순 (useCodes/CodeSelect가 사용) */
    public List<CodeVO> selectComboList(CodeVO vo) {
        return commonDAO.selectList("codeDAO.selectComboList", vo);
    }

    /**
     * 하위코드추가용 다음 값 계산: 부모(pCodeId) 기준 다음 코드ID(접두어+연번) + 다음 정렬순서(max+1).
     * 부모 코드ID의 끝 '0…'을 접두어/자릿수로 해석(MENU00→접두어 MENU·2자리 → MENU05).
     */
    public CodeVO nextChildCode(CodeVO vo) {
        List<CodeVO> children = commonDAO.selectList("codeDAO.selectChildCodes", vo);
        String parent = vo.getPCodeId() == null ? "" : vo.getPCodeId();
        Matcher m = Pattern.compile("^(.*?)(0+)$").matcher(parent);
        String prefix = parent;
        int width = 2;
        if (m.matches()) {
            prefix = m.group(1);
            width = m.group(2).length();
        }
        int maxNum = 0;
        int maxOrdr = 0;
        for (CodeVO c : children) {
            String id = c.getDbKey();
            // 코드ID 연번은 삭제분 포함 최대값(PK 충돌 방지)
            if (id != null && id.length() > prefix.length() && id.startsWith(prefix)) {
                try {
                    maxNum = Math.max(maxNum, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignore) {
                    // 접두어 뒤가 숫자가 아니면 스킵
                }
            }
            // 정렬순서는 사용중(use_yn='Y')만
            if ("Y".equals(c.getUseYn())) {
                try {
                    maxOrdr = Math.max(maxOrdr, Integer.parseInt(c.getOrdr()));
                } catch (NumberFormatException ignore) {
                    // ordr 파싱 실패 스킵
                }
            }
        }
        CodeVO res = new CodeVO();
        res.setPCodeId(parent);
        res.setDbKey(prefix + String.format("%0" + width + "d", maxNum + 1));
        res.setOrdr(String.valueOf(maxOrdr + 1));
        return res;
    }

    public void insert(CodeVO vo) {
        commonDAO.insert("codeDAO.insert", vo);
    }

    public void update(CodeVO vo) {
        commonDAO.update("codeDAO.update", vo);
    }

    /**
     * 같은 부모 내 인접 코드와 ordr 교환(위로/아래로). 끝이면 무시.
     * unique(부모, ordr) 회피: 임시값(-1) 3단계 교환. 원자성 위해 트랜잭션.
     */
    @Transactional
    public void swapOrdr(CodeVO vo) {
        CodeVO cur = commonDAO.selectOne("codeDAO.selectView", vo); // dbKey → pCodeId, ordr
        if (cur == null) {
            return;
        }
        cur.setSearchCondition(vo.getSearchCondition()); // UP/DOWN
        CodeVO adj = commonDAO.selectOne("codeDAO.selectAdjacentOrdr", cur);
        if (adj == null) {
            return; // 목록 끝
        }
        setOrdr(cur.getDbKey(), "-1");
        setOrdr(adj.getDbKey(), cur.getOrdr());
        setOrdr(cur.getDbKey(), adj.getOrdr());
    }

    private void setOrdr(String dbKey, String ordr) {
        CodeVO v = new CodeVO();
        v.setDbKey(dbKey);
        v.setOrdr(ordr);
        commonDAO.update("codeDAO.updateordr", v);
    }

    /** 삭제(논리) + 같은 부모 내 뒤 순서 당김 */
    @Transactional
    public void delete(CodeVO vo) {
        commonDAO.delete("codeDAO.delete", vo);
        commonDAO.update("codeDAO.shiftOrdrAfterDelete", vo);
    }
}
