package com.pwsh.global.file;

import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 저장소. 업로드 파일을 file.upload-dir 아래 날짜 서브폴더(yyyy/MM)에 UUID명으로 저장.
 * DB(t_file)에는 루트 제외 상대경로(file_path=서브폴더)와 저장파일명(file_str_nm)만 기록 → 이식성 확보.
 */
@Component
public class FileStorage {

    private static final DateTimeFormatter SUBDIR_FMT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;

    public FileStorage(@Value("${file.upload-dir:uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리 생성 실패: " + root, e);
        }
    }

    public String getRootPath() {
        return root.toString();
    }

    /** 저장 결과 (상대 서브폴더/저장파일명/원본명/확장자/크기) */
    public record Stored(String subDir, String storedName, String originalName, String ext, long size) {
    }

    /** 파일 저장 → 날짜 서브폴더(yyyy/MM)에 저장하고 메타 반환 */
    public Stored store(MultipartFile file) {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        String stored = UUID.randomUUID().toString() + (ext == null ? "" : "." + ext);
        String subDir = LocalDate.now().format(SUBDIR_FMT); // 예: 2026/07
        try {
            Path dir = root.resolve(subDir).normalize();
            if (!dir.startsWith(root)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일 경로입니다.");
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(stored).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new Stored(subDir, stored, original, ext == null ? "" : ext, file.getSize());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일 저장 실패: " + original);
        }
    }

    /**
     * 물리 파일 삭제. 삭제 성공 또는 애초에 없던 경우 true, 삭제 실패(권한 등) 시 false.
     * 실패해도 예외를 던지지 않음 — 상위에서 실패 시 DB row는 유지해 재정리 가능하게.
     */
    public boolean delete(String subDir, String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return true;
        }
        try {
            Path base = (subDir == null || subDir.isBlank()) ? root : root.resolve(subDir).normalize();
            Path target = base.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                return false; // 경로 이탈 방지
            }
            Files.deleteIfExists(target);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 상대 서브폴더(subDir, 예 "2026/07")와 저장파일명으로 로드.
     * subDir이 비어 있으면 루트에서 로드(서브폴더 도입 이전 파일 호환).
     */
    public Resource load(String subDir, String storedName) {
        try {
            Path base = (subDir == null || subDir.isBlank()) ? root : root.resolve(subDir).normalize();
            Path target = base.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일 경로입니다.");
            }
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "파일을 찾을 수 없습니다.");
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
    }
}
