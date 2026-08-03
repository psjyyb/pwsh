package com.pwsh.domain.hobby.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.bbsinfo.service.BbsinfoVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취미 카탈로그 업무 로직(단일 @Service). 목록/조회는 공개(SecurityConfig permitAll),
 * 등록/수정/삭제는 관리자(PermissionInterceptor: /adm/hobby 메뉴 권한).
 */
@Service
@RequiredArgsConstructor
public class HobbyService {

    private final CommonDAO commonDAO;

    public List<HobbyVO> selectList(HobbyVO vo) {
        return commonDAO.selectList("hobbyDAO.selectList", vo);
    }

    public int selectListTotCnt(HobbyVO vo) {
        return commonDAO.selectOne("hobbyDAO.selectListTotCnt", vo);
    }

    public HobbyVO selectView(HobbyVO vo) {
        return commonDAO.selectOne("hobbyDAO.selectView", vo);
    }

    /**
     * 취미 등록. 연결 게시판 미지정 시 취미 전용 게시판(일반형)을 자동 생성해 연결한다.
     * → 취미를 추가하면 곧바로 게시판(소통)·모집·레벨을 갖춘 커뮤니티가 된다.
     */
    @Transactional
    public void insert(HobbyVO vo) {
        if (vo.getBbsinfoId() == null || vo.getBbsinfoId().isBlank()) {
            BbsinfoVO board = new BbsinfoVO();
            board.setBbsinfoNm(vo.getHobbyNm());
            board.setBbsinfoCd("BBSINFO001"); // 일반형(글+댓글+답글+첨부)
            board.setBbsinfoDesc(vo.getHobbyNm() + " 게시판");
            board.setListCnt("10");
            board.setFileYn("Y");
            board.setFileCnt("5");
            board.setFileSize("10");
            board.setNoticeYn("N");
            board.setNewCnt("7");
            commonDAO.insert("bbsinfoDAO.insert", board); // useGeneratedKeys → board.dbKey
            vo.setBbsinfoId(board.getDbKey());
        }
        commonDAO.insert("hobbyDAO.insert", vo);
    }

    public void update(HobbyVO vo) {
        commonDAO.update("hobbyDAO.update", vo);
    }

    /** 삭제(논리) + 대표이미지·본문이미지(HOBBY/HOBBY_EDITOR) use_yn='N' 전파(GC 대상). */
    @Transactional
    public void delete(HobbyVO vo) {
        commonDAO.delete("hobbyDAO.delete", vo);
        commonDAO.update("fileDAO.deactivateFilesByOwner",
                Map.of("mapKey", vo.getDbKey(), "locs", List.of("HOBBY", "HOBBY_EDITOR")));
    }
}
