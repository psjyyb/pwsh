package com.pwsh.domain.policy.service;

import com.pwsh.common.CommonDAO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관/정책 업무 로직. 컨트롤러는 매핑만, 로직·트랜잭션은 여기(단일 @Service).
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final CommonDAO commonDAO;

    public List<PolicyVO> selectList(PolicyVO vo) {
        return commonDAO.selectList("policyDAO.selectList", vo);
    }

    public int selectListTotalCount(PolicyVO vo) {
        return commonDAO.selectOne("policyDAO.selectListTotalCount", vo);
    }

    public PolicyVO selectView(PolicyVO vo) {
        return commonDAO.selectOne("policyDAO.selectView", vo);
    }

    public void insert(PolicyVO vo) {
        commonDAO.insert("policyDAO.insert", vo);
    }

    public void update(PolicyVO vo) {
        commonDAO.update("policyDAO.update", vo);
    }

    /** 같은 약관유형 내 인접 약관과 ordr 교환. 임시값(-1) 3단계, 트랜잭션. */
    @Transactional
    public void swapOrdr(PolicyVO vo) {
        PolicyVO cur = commonDAO.selectOne("policyDAO.selectView", vo);
        if (cur == null) {
            return;
        }
        cur.setDirection(vo.getDirection());
        PolicyVO adj = commonDAO.selectOne("policyDAO.selectAdjacentOrdr", cur);
        if (adj == null) {
            return;
        }
        setOrdr(cur.getRowId(), "-1");
        setOrdr(adj.getRowId(), cur.getOrdr());
        setOrdr(cur.getRowId(), adj.getOrdr());
    }

    private void setOrdr(String rowId, String ordr) {
        PolicyVO v = new PolicyVO();
        v.setRowId(rowId);
        v.setOrdr(ordr);
        commonDAO.update("policyDAO.updateordr", v);
    }

    /** 삭제(논리) + 같은 약관유형 내 뒤 순서 당김 */
    @Transactional
    public void delete(PolicyVO vo) {
        commonDAO.delete("policyDAO.delete", vo);
        commonDAO.update("policyDAO.shiftOrdrAfterDelete", vo);
    }
}
