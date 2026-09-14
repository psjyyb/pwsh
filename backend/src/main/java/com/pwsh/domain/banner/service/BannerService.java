package com.pwsh.domain.banner.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.file.service.FileVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배너(메인 히어로 슬라이드) 업무 로직. 컨트롤러는 매핑만, 로직·트랜잭션은 여기(단일 @Service).
 * 배경 이미지는 file_ref(file_type=BANNER)로 동기화, 삭제 시 파일 use_yn='N' 전파.
 */
@Service
@RequiredArgsConstructor
public class BannerService {

    /** 파일 용도 코드 — file_ref.file_type */
    private static final String FILE_TYPE = "BANNER";

    private final CommonDAO commonDAO;

    public List<BannerVO> selectList(BannerVO vo) {
        return commonDAO.selectList("bannerDAO.selectList", vo);
    }

    public int selectListTotalCount(BannerVO vo) {
        return commonDAO.selectOne("bannerDAO.selectListTotalCount", vo);
    }

    public BannerVO selectView(BannerVO vo) {
        return commonDAO.selectOne("bannerDAO.selectView", vo);
    }

    /** 사용자 메인 노출용(사용중 + 노출기간 내). 비로그인도 읽는다 */
    public List<BannerVO> selectMainList(BannerVO vo) {
        return commonDAO.selectList("bannerDAO.selectMainList", vo);
    }

    @Transactional
    public void insert(BannerVO vo) {
        commonDAO.insert("bannerDAO.insert", vo); // useGeneratedKeys → rowId=banner_id
        syncImage(vo.getRowId(), vo.getFileId());
    }

    @Transactional
    public void update(BannerVO vo) {
        commonDAO.update("bannerDAO.update", vo);
        syncImage(vo.getRowId(), vo.getFileId());
    }

    /** 배경 이미지(단일)를 file_ref로 동기화 — 기존 매핑 제거 후 이미지가 있으면 등록. */
    private void syncImage(String bannerId, String fileId) {
        FileVO m = new FileVO();
        m.setMapKey(bannerId);
        m.setFileType(FILE_TYPE);
        commonDAO.delete("fileDAO.deleteRfileByMap", m);
        if (fileId != null && !fileId.isBlank()) {
            m.setFileId(fileId);
            m.setSortNo("0");
            commonDAO.insert("fileDAO.insertRfile", m);
        }
    }

    /** 인접 배너와 sortNo 교환. 임시값(-1) 3단계, 트랜잭션(팝업·메뉴와 같은 방식). */
    @Transactional
    public void swapSort(BannerVO vo) {
        BannerVO cur = commonDAO.selectOne("bannerDAO.selectView", vo);
        if (cur == null) {
            return;
        }
        cur.setDirection(vo.getDirection());
        BannerVO adj = commonDAO.selectOne("bannerDAO.selectAdjacentSort", cur);
        if (adj == null) {
            return; // 맨 위/아래면 할 일이 없다
        }
        setSortNo(cur.getRowId(), "-1");
        setSortNo(adj.getRowId(), cur.getSortNo());
        setSortNo(cur.getRowId(), adj.getSortNo());
    }

    private void setSortNo(String rowId, String sortNo) {
        BannerVO v = new BannerVO();
        v.setRowId(rowId);
        v.setSortNo(sortNo);
        commonDAO.update("bannerDAO.updatesort", v);
    }

    /**
     * 삭제(논리) + 뒤 순서 당김 + 배경 이미지 use_yn='N' 전파.
     * ★ 파일 전파를 빼먹으면 고아 파일 GC에 걸리지 않아 영구 누수된다(CLAUDE.md 규칙).
     */
    @Transactional
    public void delete(BannerVO vo) {
        commonDAO.delete("bannerDAO.delete", vo);
        commonDAO.update("bannerDAO.shiftSortAfterDelete", vo);
        commonDAO.update("fileDAO.deactivateFilesByOwner",
                Map.of("mapKey", vo.getRowId(), "locs", List.of(FILE_TYPE)));
    }
}
