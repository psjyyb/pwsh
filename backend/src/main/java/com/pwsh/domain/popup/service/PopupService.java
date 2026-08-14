package com.pwsh.domain.popup.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.file.service.FileVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팝업 업무 로직. 컨트롤러는 매핑만, 로직·트랜잭션은 여기(단일 @Service).
 * 팝업 이미지는 r_file(file_loc=POPUP)로 동기화, 삭제 시 파일 use_yn='N' 전파.
 */
@Service
@RequiredArgsConstructor
public class PopupService {

    private final CommonDAO commonDAO;

    public List<PopupVO> selectList(PopupVO vo) {
        return commonDAO.selectList("popupDAO.selectList", vo);
    }

    public int selectListTotalCount(PopupVO vo) {
        return commonDAO.selectOne("popupDAO.selectListTotalCount", vo);
    }

    public PopupVO selectView(PopupVO vo) {
        return commonDAO.selectOne("popupDAO.selectView", vo);
    }

    /** 사용자 메인 노출용(사용중 + 노출기간 내) */
    public List<PopupVO> selectMainList(PopupVO vo) {
        return commonDAO.selectList("popupDAO.selectMainList", vo);
    }

    @Transactional
    public void insert(PopupVO vo) {
        commonDAO.insert("popupDAO.insert", vo); // useGeneratedKeys → rowId=pop_id
        syncPopupImage(vo.getRowId(), vo.getFileId());
    }

    @Transactional
    public void update(PopupVO vo) {
        commonDAO.update("popupDAO.update", vo);
        syncPopupImage(vo.getRowId(), vo.getFileId());
    }

    /** 팝업 이미지(단일)를 r_file(POPUP)로 동기화 — 기존 매핑 제거 후 이미지 있으면 등록. */
    private void syncPopupImage(String popId, String fileId) {
        FileVO m = new FileVO();
        m.setMapKey(popId);
        m.setFileLoc("POPUP");
        commonDAO.delete("fileDAO.deleteRfileByMap", m);
        if (fileId != null && !fileId.isBlank()) {
            m.setFileId(fileId);
            m.setOrdr("0");
            commonDAO.insert("fileDAO.insertRfile", m);
        }
    }

    /** 인접 팝업과 ordr 교환. 임시값(-1) 3단계, 트랜잭션. */
    @Transactional
    public void swapOrdr(PopupVO vo) {
        PopupVO cur = commonDAO.selectOne("popupDAO.selectView", vo);
        if (cur == null) {
            return;
        }
        cur.setDirection(vo.getDirection());
        PopupVO adj = commonDAO.selectOne("popupDAO.selectAdjacentOrdr", cur);
        if (adj == null) {
            return;
        }
        setOrdr(cur.getRowId(), "-1");
        setOrdr(adj.getRowId(), cur.getOrdr());
        setOrdr(cur.getRowId(), adj.getOrdr());
    }

    private void setOrdr(String rowId, String ordr) {
        PopupVO v = new PopupVO();
        v.setRowId(rowId);
        v.setOrdr(ordr);
        commonDAO.update("popupDAO.updateordr", v);
    }

    /** 삭제(논리) + 뒤 순서 당김(전역) + 팝업 이미지 use_yn='N' 전파(GC가 보존기간 후 정리) */
    @Transactional
    public void delete(PopupVO vo) {
        commonDAO.delete("popupDAO.delete", vo);
        commonDAO.update("popupDAO.shiftOrdrAfterDelete", vo);
        commonDAO.update("fileDAO.deactivateFilesByOwner",
                Map.of("mapKey", vo.getRowId(), "locs", List.of("POPUP")));
    }
}
