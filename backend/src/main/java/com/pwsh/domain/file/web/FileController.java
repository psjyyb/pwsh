package com.pwsh.domain.file.web;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드/다운로드/삭제 + 엔티티 매핑(file_ref) + 고아 정리 + 미디어 라이브러리 —
 * 컨트롤러는 매핑만, 로직은 {@link FileService}.
 * 표준 CRUD 틀에 안 맞는 특수 컨트롤러(멀티파트 업로드·바이너리 다운로드·유지보수 gc).
 *
 * <p>⚠ 이 도메인은 {@code PermissionInterceptor}의 메뉴권한 검사에서 통째로 면제돼 있다
 * (게시판 첨부 때문에 일반 회원도 업로드·다운로드를 해야 한다). 그래서 <b>관리 기능은
 * 반드시 {@link #requireAdmin()}을 직접 불러야 한다</b> — 빠뜨리면 로그인만 하면 다 열린다.
 */
@RestController
@RequestMapping("/api/adm/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final com.pwsh.global.security.GenAccessGuard genAccessGuard;

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

    /** 업로드(다중) → 저장 + file 등록, 생성된 파일 메타 반환 */
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

    /** 라이브러리 업로드(관리자 전용) — 엔티티에 안 붙어도 보관되도록 LIBRARY 매핑까지 건다 */
    @RequestMapping("/uploadLibrary.do")
    public ApiResponse<List<FileVO>> uploadLibrary(@RequestParam("files") MultipartFile[] files) {
        requireAdmin();
        return ApiResponse.ok(fileService.uploadLibrary(files));
    }

    /**
     * 목록 계열(관리자 전용). ''=미디어 라이브러리 목록 / Ref=파일 1건의 사용처 목록.
     */
    @RequestMapping("/selectFileList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) FileVO searchVO) {
        requireAdmin();
        FileVO vo = searchVO == null ? new FileVO() : searchVO;
        if ("Ref".equals(variant)) {
            Validate.required(vo.getFileId(), "파일");
            return ApiResponse.ok(fileService.selectRefs(vo));
        }
        int totalCount = fileService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", fileService.selectList(vo), "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    /** 수정 계열(관리자 전용). Library=라이브러리 담기/빼기(useYn='Y'면 담기) */
    @RequestMapping("/updateFile{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody FileVO searchVO) {
        requireAdmin();
        if ("Library".equals(variant)) {
            Validate.required(searchVO.getFileId(), "파일");
            fileService.setLibrary(searchVO, "Y".equals(searchVO.getUseYn()));
        }
        return ApiResponse.ok();
    }

    /** 다운로드 (인증 필요 → 프론트는 axios blob로 호출) */
    @GetMapping("/download.do")
    public ResponseEntity<Resource> download(@RequestParam String fileId) {
        FileVO param = new FileVO();
        param.setFileId(fileId);
        FileVO file = fileService.selectView(param);
        if (file == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Messages.get("error.file.notFound"));
        }
        fileService.assertServable(file); // 첨부는 소속 게시판 접근권자만 다운로드(IDOR 차단)
        Resource resource = fileService.loadResource(file);
        String encoded = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
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

    /**
     * 엔티티(map_key+file_type)에 연결된 파일 목록.
     *
     * <p>게시글 첨부(POST*)는 <b>그 글의 열람 권한</b>으로 판정한다 — 공개 글이면 비로그인도 목록을 볼 수 있고
     * (막아두면 게스트가 첨부 있는 글을 열자마자 401로 로그인 화면에 튕긴다), 접근 권한이 없는 게시판의
     * 글이면 로그인 회원이라도 파일명조차 얻지 못한다. 그 외 위치(팝업·로고)는 공개 자산이다.
     */
    @RequestMapping("/selectFileMapList.do")
    public ApiResponse<List<FileVO>> selectFileMapList(@RequestBody FileVO searchVO) {
        String loc = searchVO.getFileType();
        if (loc != null && loc.startsWith("POST")) {
            genAccessGuard.checkPost(searchVO.getMapKey());
        }
        return ApiResponse.ok(fileService.selectFilesByMap(searchVO));
    }

    /** 엔티티-파일 매핑 저장(기존 삭제 후 재등록 + 빠진 파일 즉시정리) */
    @RequestMapping("/saveFileMapping.do")
    public ApiResponse<Void> saveFileMapping(@RequestBody FileVO searchVO) {
        fileService.saveFileMapping(searchVO);
        return ApiResponse.ok();
    }
}
