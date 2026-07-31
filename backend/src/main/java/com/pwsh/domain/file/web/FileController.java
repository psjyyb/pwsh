package com.pwsh.domain.file.web;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.file.service.FileService;
import com.pwsh.domain.file.service.FileVO;
import com.pwsh.global.security.SecurityUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드/다운로드/삭제 + 엔티티 매핑(r_file) + 고아 정리 — 컨트롤러는 매핑만, 로직은 {@link FileService}.
 * 표준 CRUD 틀에 안 맞는 특수 컨트롤러(멀티파트 업로드·바이너리 다운로드·유지보수 gc).
 */
@RestController
@RequestMapping("/api/adm/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 파일 관리(정리·삭제·목록)는 관리자 전용. (업로드/다운로드/매핑은 게시판 작성 흐름이라 로그인만) */
    private void requireAdmin() {
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 고아 파일 즉시 정리(수동 트리거, 관리자 전용). 반환: 삭제 건수 */
    @RequestMapping("/gc.do")
    public ApiResponse<Integer> gc() {
        requireAdmin();
        return ApiResponse.ok(fileService.sweep());
    }

    /** 업로드(다중) → 저장 + t_file 등록, 생성된 파일 메타 반환 */
    @RequestMapping("/upload.do")
    public ApiResponse<List<FileVO>> upload(@RequestParam("files") MultipartFile[] files) {
        return ApiResponse.ok(fileService.upload(files));
    }

    /** 에디터 본문 이미지 업로드 → 공개 서빙 URL 반환. */
    @RequestMapping("/imageUpload.do")
    public ApiResponse<Map<String, String>> imageUpload(@RequestParam("file") MultipartFile file) {
        FileVO vo = fileService.imageUpload(file);
        return ApiResponse.ok(Map.of("url", "/api/pub/image/" + vo.getFileId()));
    }

    /** 목록(파일 관리 화면, 관리자 전용) */
    @RequestMapping("/selectFileList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody(required = false) FileVO searchVO) {
        requireAdmin();
        FileVO vo = searchVO == null ? new FileVO() : searchVO;
        int totCnt = fileService.selectListTotCnt(vo);
        return ApiResponse.ok(Map.of(
                "list", fileService.selectList(vo), "totCnt", totCnt,
                "page", PageUtil.of(vo.getPageIndex(), vo.getSize(), totCnt)));
    }

    /** 다운로드 (인증 필요 → 프론트는 axios blob로 호출) */
    @GetMapping("/download.do")
    public ResponseEntity<Resource> download(@RequestParam String fileId) {
        FileVO param = new FileVO();
        param.setFileId(fileId);
        FileVO file = fileService.selectView(param);
        if (file == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        fileService.assertServable(file); // 첨부는 소속 게시판 접근권자만 다운로드(IDOR 차단)
        Resource resource = fileService.loadResource(file);
        String encoded = URLEncoder.encode(file.getFileOrgNm(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(resource);
    }

    /** 삭제(논리, 관리자 전용) */
    @RequestMapping("/deleteFile.do")
    public ApiResponse<Void> delete(@RequestBody FileVO searchVO) {
        requireAdmin();
        fileService.delete(searchVO);
        return ApiResponse.ok();
    }

    /** 엔티티(map_key+file_loc)에 연결된 파일 목록 */
    @RequestMapping("/selectFileMapList.do")
    public ApiResponse<List<FileVO>> selectFileMapList(@RequestBody FileVO searchVO) {
        return ApiResponse.ok(fileService.selectFilesByMap(searchVO));
    }

    /** 엔티티-파일 매핑 저장(기존 삭제 후 재등록 + 빠진 파일 즉시정리) */
    @RequestMapping("/saveFileMapping.do")
    public ApiResponse<Void> saveFileMapping(@RequestBody FileVO searchVO) {
        fileService.saveFileMapping(searchVO);
        return ApiResponse.ok();
    }
}
