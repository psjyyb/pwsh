package com.pwsh.domain.file.web;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.domain.file.service.FileService;
import com.pwsh.domain.file.service.FileVO;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 읽기 전용 이미지 서빙 (인증 불필요, SecurityConfig /api/pub/** permitAll).
 * 에디터 본문 &lt;img src&gt;용. 컨트롤러는 매핑만, 조회/로드는 {@link FileService}.
 */
@RestController
@RequestMapping("/api/pub/image")
@RequiredArgsConstructor
public class PubImageController {

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final FileService fileService;

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> image(@PathVariable String fileId) {
        FileVO param = new FileVO();
        param.setFileId(fileId);
        FileVO file = fileService.selectView(param);
        if (file == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
        String ext = file.getExt() == null ? "" : file.getExt().toLowerCase();
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일이 아닙니다.");
        }
        fileService.assertServable(file); // 순차 id 열거(IDOR) 차단 — 연결 콘텐츠 접근권으로 서빙 가부 판정
        Resource resource = fileService.loadResource(file);
        return ResponseEntity.ok()
                .contentType(contentType(ext))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
                .body(resource);
    }

    private MediaType contentType(String ext) {
        return switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            default -> MediaType.IMAGE_JPEG; // jpg/jpeg
        };
    }
}
