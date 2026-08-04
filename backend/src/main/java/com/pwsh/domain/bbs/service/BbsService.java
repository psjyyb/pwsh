package com.pwsh.domain.bbs.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 업무 로직. 컨트롤러는 매핑만(단일 @Service).
 * 비밀글 열람 인가·조회수 증가·삭제 시 파일 use_yn='N' 전파를 담당.
 * 조회 진입부에서 게시판 단위 접근 인가(GenAccessGuard) — 비회원/회원 딥링크 차단.
 */
@Service
@RequiredArgsConstructor
public class BbsService {

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;
    private final PasswordEncoder passwordEncoder;

    public List<BbsVO> selectList(BbsVO vo) {
        genAccessGuard.checkBoard(vo.getBbsinfoId());
        vo.setViewerId(viewerId()); // mine_yn(내 글) 판정용 — 목록의 수정/삭제·비밀글 게이트에 사용
        return commonDAO.selectList("bbsDAO.selectList", vo);
    }

    /** 주간 인기글(메인 '이번 주 베스트') — 취미 공개 게시판·비밀글 제외라 접근가드 불필요(공개). */
    public List<BbsVO> selectListWeeklyBest() {
        return commonDAO.selectList("bbsDAO.selectListWeeklyBest", new BbsVO());
    }

    /** 내가 쓴 글(마이페이지) — 본인 reg_id 기준 전 게시판(원글). */
    public List<BbsVO> selectListMine() {
        BbsVO vo = new BbsVO();
        vo.setRegId(SecurityUtil.getCurrentUserId());
        return commonDAO.selectList("bbsDAO.selectListMine", vo);
    }

    public int selectListTotCnt(BbsVO vo) {
        genAccessGuard.checkBoard(vo.getBbsinfoId());
        vo.setViewerId(viewerId()); // 목록과 동일한 차단 필터를 적용해 총건수 일치
        return commonDAO.selectOne("bbsDAO.selectListTotCnt", vo);
    }

    /**
     * 상세. 비밀글은 작성자·관리자(MEM02)가 아니면 비번(bbsPw) 일치 시에만 내용 반환.
     * 잠금 상태면 내용을 비우고 secretLocked='Y'. 열람 허용 + viewUp='Y'일 때만 조회수 증가.
     */
    public BbsVO selectView(BbsVO vo) {
        vo.setViewerId(viewerId()); // 좋아요 여부(liked_yn) 판정용
        BbsVO post = commonDAO.selectOne("bbsDAO.selectView", vo);
        if (post == null) {
            return null;
        }
        genAccessGuard.checkBoard(post.getBbsinfoId());
        if ("Y".equals(post.getSecretYn())) {
            String me = SecurityUtil.getCurrentUserId();
            boolean owner = me != null && me.equals(post.getRegId());
            boolean admin = "MEM02".equals(SecurityUtil.getCurrentMemCd());
            if (!owner && !admin) {
                String inputPw = vo.getBbsPw();
                boolean pwOk = inputPw != null && post.getBbsPw() != null
                        && passwordEncoder.matches(inputPw, post.getBbsPw());
                if (!pwOk) {
                    BbsVO locked = new BbsVO();
                    locked.setDbKey(post.getDbKey());
                    locked.setBbsinfoId(post.getBbsinfoId());
                    locked.setTitle("비밀글입니다.");
                    locked.setSecretYn("Y");
                    locked.setSecretLocked("Y");
                    return locked;
                }
            }
        }
        if ("Y".equals(vo.getViewUp())) {
            commonDAO.update("bbsDAO.updateViewCnt", vo);
        }
        post.setBbsPw(null); // 비밀번호는 응답에서 제외
        post.setRegId(null); // 작성자 로그인 ID는 응답에서 제외(공개 API — handle/mineYn으로 대체)
        return post;
    }

    /**
     * 등록 후 생성된 게시글 ID는 vo.dbKey에 세팅됨(useGeneratedKeys). 접근 불가 게시판엔 작성 차단.
     * pBbsId가 있으면 답글 — 원글의 게시판을 상속하고 depth+1로 저장(스레드 표시는 목록 CTE가 처리).
     */
    public void insert(BbsVO vo) {
        String pId = vo.getPBbsId();
        if (pId != null && !pId.isEmpty() && !"0".equals(pId)) {
            BbsVO key = new BbsVO();
            key.setDbKey(pId);
            BbsVO parent = commonDAO.selectOne("bbsDAO.selectView", key);
            if (parent == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "원글을 찾을 수 없습니다.");
            }
            vo.setBbsinfoId(parent.getBbsinfoId());
            int depth = parent.getBbsDepth() == null ? 1 : Integer.parseInt(parent.getBbsDepth());
            vo.setBbsDepth(String.valueOf(depth + 1));
            genAccessGuard.checkBoard(parent.getBbsinfoId());
        } else {
            genAccessGuard.checkBoard(vo.getBbsinfoId());
        }
        encodeBbsPw(vo);
        commonDAO.insert("bbsDAO.insert", vo);
    }

    /** 비밀글 비번은 BCrypt로 저장(계정 비번과 동일). 값이 있을 때만 인코딩(빈값=변경없음). */
    private void encodeBbsPw(BbsVO vo) {
        if (vo.getBbsPw() != null && !vo.getBbsPw().isEmpty()) {
            vo.setBbsPw(passwordEncoder.encode(vo.getBbsPw()));
        }
    }

    /** 수정 — 작성자 본인·관리자만(IDOR 방지), 소속 게시판 접근권 확인. */
    public void update(BbsVO vo) {
        loadForModify(vo);
        encodeBbsPw(vo);
        commonDAO.update("bbsDAO.update", vo);
    }

    /** 삭제(논리) + 이 글의 파일(첨부/갤러리/에디터) use_yn='N' 전파(GC가 보존기간 후 정리). 작성자·관리자만. */
    @Transactional
    public void delete(BbsVO vo) {
        loadForModify(vo);
        commonDAO.delete("bbsDAO.delete", vo);
        commonDAO.update("fileDAO.deactivateFilesByOwner",
                Map.of("mapKey", vo.getDbKey(), "locs", List.of("BBS", "BBS_IMG", "BBS_EDITOR")));
    }

    /** 현재 조회자 id(비로그인/system은 null → liked_yn 'N'). */
    private String viewerId() {
        String me = SecurityUtil.getCurrentUserId();
        return (me == null || "system".equals(me)) ? null : me;
    }

    /** 수정/삭제 공통: 대상 로드 + 게시판 접근권 + 작성자/관리자 인가. 없으면 예외. */
    private BbsVO loadForModify(BbsVO vo) {
        BbsVO post = commonDAO.selectOne("bbsDAO.selectView", vo);
        if (post == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        genAccessGuard.checkBoard(post.getBbsinfoId());
        SecurityUtil.assertOwnerOrAdmin(post.getRegId());
        return post;
    }
}
