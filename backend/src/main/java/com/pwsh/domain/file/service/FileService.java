package com.pwsh.domain.file.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.file.FileStorage;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 도메인 단일 서비스 — 업로드/조회/삭제 + 엔티티 매핑(r_file) + 고아 파일 정리(GC).
 * (기존 FileGcService를 여기로 흡수해 도메인당 하나의 Service로 통일.) 저장은 FileStorage(로컬).
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final CommonDAO commonDAO;
    private final FileStorage fileStorage;
    private final GenAccessGuard genAccessGuard;

    /** C 보존기간(일) — 삭제된 엔티티 파일 정리 유예 */
    @Value("${file.gc.retention-days:180}")
    private int retentionDays;

    /** A 유예(시간) — 업로드 후 미저장 파일 정리 유예 */
    @Value("${file.gc.abandon-hours:24}")
    private int abandonHours;

    // ===== 업로드 =====

    /** 업로드(다중) → 저장 + t_file 등록, 생성된 파일 메타 반환 */
    public List<FileVO> upload(MultipartFile[] files) {
        List<FileVO> result = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f.isEmpty()) {
                continue;
            }
            result.add(store(f));
        }
        return result;
    }

    /** 에디터 본문 이미지 업로드 → 저장 + t_file 등록(이미지 확장자만 허용) */
    public FileVO imageUpload(MultipartFile file) {
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String lower = ext == null ? "" : ext.toLowerCase();
        if (!IMAGE_EXTS.contains(lower)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일만 업로드할 수 있습니다.");
        }
        return store(file);
    }

    private FileVO store(MultipartFile f) {
        FileStorage.Stored s = fileStorage.store(f);
        FileVO vo = new FileVO();
        vo.setFilePath(s.subDir()); // 루트 제외 상대경로(날짜 서브폴더)
        vo.setFileStrNm(s.storedName());
        vo.setFileOrgNm(s.originalName());
        vo.setFileExt(s.ext());
        vo.setFileSize(String.valueOf(s.size()));
        commonDAO.insert("fileDAO.insert", vo); // useGeneratedKeys → vo.fileId
        return vo;
    }

    // ===== 조회/삭제 =====

    public List<FileVO> selectList(FileVO vo) {
        return commonDAO.selectList("fileDAO.selectList", vo);
    }

    public int selectListTotalCount(FileVO vo) {
        return commonDAO.selectOne("fileDAO.selectListTotalCount", vo);
    }

    public FileVO selectView(FileVO vo) {
        return commonDAO.selectOne("fileDAO.selectView", vo);
    }

    /** 저장 파일 리소스 로드(다운로드·공개 이미지 서빙용) */
    public Resource loadResource(FileVO meta) {
        return fileStorage.load(meta.getFilePath(), meta.getFileStrNm());
    }

    /**
     * 파일 접근 인가(공개 이미지 서빙·다운로드 공통). 파일이 연결된 콘텐츠 권한으로 판정 — 순차 id 열거(IDOR) 차단.
     * - 관리자: 통과
     * - POPUP 매핑: 공개(팝업은 메인에 전원 노출)
     * - BBS* 매핑(첨부/갤러리/에디터): 그 게시글이 속한 게시판 접근권(GenAccessGuard.canAccessPost)
     * - 매핑 없음(편집 중 갓 업로드): 로그인 사용자만 허용(익명 열거 차단)
     * 어디에도 접근권이 없으면 403.
     */
    public void assertServable(FileVO file) {
        if (SecurityUtil.isAdmin()) {
            return;
        }
        // 프로필 사진은 공개 서빙(r_file 매핑 없이 t_user.profile_file_id 직접 참조 — 게시글 작성자 아바타 등)
        Integer profileRefs = commonDAO.selectOne("fileDAO.countProfileRefs", file);
        if (profileRefs != null && profileRefs > 0) {
            return;
        }
        List<FileVO> refs = commonDAO.selectList("fileDAO.selectRefsByFileId", file);
        if (refs.isEmpty()) {
            if (!SecurityUtil.isAuthenticated()) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            return; // 미매핑 = 편집 중 미리보기(로그인 사용자)
        }
        for (FileVO r : refs) {
            String loc = r.getFileLoc();
            if ("POPUP".equals(loc) || "LOGO".equals(loc) || (loc != null && loc.startsWith("HOBBY"))) {
                return; // 공개 콘텐츠(팝업·로고·취미 대표이미지·취미 본문이미지는 비로그인에도 노출)
            }
            if (loc != null && loc.startsWith("BBS") && genAccessGuard.canAccessPost(r.getMapKey())) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    /** 삭제(논리) */
    public void delete(FileVO vo) {
        commonDAO.delete("fileDAO.delete", vo);
    }

    /** 엔티티(map_key+file_loc)에 연결된 파일 목록 */
    public List<FileVO> selectFilesByMap(FileVO vo) {
        return commonDAO.selectList("fileDAO.selectFilesByMap", vo);
    }

    // ===== 엔티티-파일 매핑 저장(B 고아 즉시정리 포함) =====

    /**
     * 매핑 재작성(기존 삭제 후 재등록). fileDescs 있으면 캡션(file_desc)도 갱신.
     * 이번 저장에서 빠진 파일은 다른 활성 참조 없으면 물리+t_file 행까지 hard-delete(저장 시에만 → 취소 안전).
     */
    @Transactional
    public void saveFileMapping(FileVO searchVO) {
        assertMapKeyOwner(searchVO); // 남의 글(map_key) 첨부를 임의 변경하는 IDOR 차단
        List<FileVO> oldFiles = commonDAO.selectList("fileDAO.selectFilesByMap", searchVO);

        commonDAO.delete("fileDAO.deleteRfileByMap", searchVO);
        String[] ids = searchVO.getFileIds();
        String[] descs = searchVO.getFileDescs();
        Set<String> newIds = new HashSet<>();
        if (ids != null) {
            for (int i = 0; i < ids.length; i++) {
                FileVO r = new FileVO();
                r.setMapKey(searchVO.getMapKey());
                r.setFileLoc(searchVO.getFileLoc());
                r.setFileId(ids[i]);
                r.setSortNo(String.valueOf(i));
                commonDAO.insert("fileDAO.insertRfile", r);
                newIds.add(ids[i]);
                if (descs != null && i < descs.length) {
                    FileVO d = new FileVO();
                    d.setFileId(ids[i]);
                    d.setFileDesc(descs[i]);
                    commonDAO.update("fileDAO.updateFileDesc", d);
                }
            }
        }

        for (FileVO of : oldFiles) {
            String fid = of.getFileId();
            if (fid == null || newIds.contains(fid)) {
                continue;
            }
            FileVO p = new FileVO();
            p.setFileId(fid);
            int refs = commonDAO.selectOne("fileDAO.countActiveRefs", p);
            if (refs > 0) {
                continue; // 다른 곳에서 사용 중 → 유지
            }
            FileVO meta = commonDAO.selectOne("fileDAO.selectView", p);
            if (meta == null) {
                continue;
            }
            if (fileStorage.delete(meta.getFilePath(), meta.getFileStrNm())) {
                commonDAO.delete("fileDAO.deleteHard", p);
            }
        }
    }

    /** 매핑 대상(map_key) 소유 검증: 게시판 콘텐츠(BBS*)는 그 글 작성자·관리자만, 그 외(팝업 등)는 관리자만. */
    private void assertMapKeyOwner(FileVO vo) {
        if (SecurityUtil.isAdmin()) {
            return;
        }
        String loc = vo.getFileLoc();
        if (loc != null && loc.startsWith("BBS")) {
            String regId = commonDAO.selectOne("fileDAO.selectPostRegId", vo); // map_key = bbs_id
            if (regId == null) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            SecurityUtil.assertOwnerOrAdmin(regId);
        } else {
            throw new BusinessException(ErrorCode.ACCESS_DENIED); // 팝업 등 관리자 콘텐츠 매핑은 관리자만
        }
    }

    // ===== 고아 파일 정리(GC) =====

    /**
     * 고아 파일 즉시 정리. DB(r_file·t_file) 먼저 제거(각 statement 커밋) → 그 다음 물리삭제(best-effort).
     * 트랜잭션 롤백이 이미 지운 파일을 되돌릴 수 없으므로, DB를 진실원본으로 두고 물리삭제는 후행/실패허용. @return 물리삭제 건수
     */
    public int sweep() {
        Map<String, Object> p = new HashMap<>();
        p.put("retentionDays", retentionDays);
        p.put("abandonHours", abandonHours);
        List<FileVO> orphans = commonDAO.selectList("fileDAO.selectOrphans", p);

        for (FileVO f : orphans) { // 1) DB 매핑/메타 제거(트랜잭션 없음 → 각자 커밋)
            commonDAO.delete("fileDAO.deleteRfileByFileId", f);
            commonDAO.delete("fileDAO.deleteHard", f);
        }
        int deleted = 0;
        for (FileVO f : orphans) { // 2) 물리삭제(커밋 후) — 실패해도 DB는 정리됨(디스크 고아만 남고 경고 로그)
            if (fileStorage.delete(f.getFilePath(), f.getFileStrNm())) {
                deleted++;
            } else {
                log.warn("[FileGC] 물리삭제 실패(디스크 고아 가능): id={}, {}/{}",
                        f.getFileId(), f.getFilePath(), f.getFileStrNm());
            }
        }
        log.info("[FileGC] 고아파일 정리: 대상 {}건, 물리삭제 {}건 (retention={}일, abandon={}시간)",
                orphans.size(), deleted, retentionDays, abandonHours);
        return deleted;
    }

    /** 매일 새벽 자동 실행(cron 설정 가능). */
    @Scheduled(cron = "${file.gc.cron:0 0 4 * * *}")
    public void scheduledSweep() {
        sweep();
    }
}
