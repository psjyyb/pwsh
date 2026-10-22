package com.pwsh.domain.destroylog.service;

import com.pwsh.common.CommonDAO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 개인정보 파기 이력 — 기록(MemberService가 파기할 때 부른다)과 관리자 조회. append-only라 수정·삭제가 없다.
 */
@Service
@RequiredArgsConstructor
public class DestroyLogService {

    /** 탈퇴 후 보존기간 경과로 배치가 파기 */
    public static final String AUTO = "DESTROY01";
    /** 관리자가 즉시 파기(정보주체 요청 등) */
    public static final String ADMIN_NOW = "DESTROY02";

    private final CommonDAO commonDAO;

    public List<DestroyLogVO> selectList(DestroyLogVO vo) {
        escapeKeyword(vo);
        return commonDAO.selectList("destroyLogDAO.selectList", vo);
    }

    public int selectListTotalCount(DestroyLogVO vo) {
        escapeKeyword(vo);
        return commonDAO.selectOne("destroyLogDAO.selectListTotalCount", vo);
    }

    /**
     * 파기 1건 기록. 호출하는 쪽의 트랜잭션 안에서 남긴다 — 파기가 롤백되면 이력도 같이 사라져야
     * "이력 = 실제로 지운 것"이 유지된다.
     *
     * @param actorId 처리자(자동 파기면 null)
     */
    public void write(String memberId, String destroyCd, String actorId) {
        DestroyLogVO vo = new DestroyLogVO();
        vo.setMemberId(memberId);
        vo.setDestroyCd(destroyCd);
        vo.setActorId(actorId);
        commonDAO.insert("destroyLogDAO.insert", vo);
    }

    /**
     * 검색어의 LIKE 특수문자를 글자 그대로(매퍼 ESCAPE '\'). 원래 검색어는 두고 이스케이프본을 따로 둔다 —
     * 컨트롤러가 같은 VO로 건수·목록을 부르므로 원본을 덮어쓰면 두 번째 호출에서 '\'가 겹친다.
     */
    private void escapeKeyword(DestroyLogVO vo) {
        String k = vo.getFilterKeyword();
        vo.setKeywordLike(k == null || k.isBlank() ? null
                : k.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
    }
}
