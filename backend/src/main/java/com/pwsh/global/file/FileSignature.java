package com.pwsh.global.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일의 실제 내용(매직바이트)이 확장자와 맞는지 검사한다.
 *
 * <p>확장자·Content-Type은 클라이언트가 정하는 값이라 위장이 쉽다(exe 를 jpg 로 바꿔 올리기).
 * 화이트리스트만으로는 이 경우를 걸러내지 못하므로, 파일 앞부분 바이트를 함께 확인한다.
 *
 * <p>판정 원칙:
 * <ul>
 *   <li>알고 있는 확장자면 → 시그니처가 맞아야 통과(불일치는 거부)</li>
 *   <li>모르는 확장자면 → 통과(허용 확장자 화이트리스트가 이미 1차 방어선이다)</li>
 * </ul>
 * 텍스트 계열(txt·csv 등)은 고정 시그니처가 없어 검사 대상이 아니다.
 */
public final class FileSignature {

    /** 확장자 → 허용 시그니처 목록(파일 선두 바이트). 하나라도 맞으면 통과. */
    private static final Map<String, List<byte[]>> SIGNATURES = new HashMap<>();

    /** 검사에 필요한 최대 선두 바이트 수(가장 긴 시그니처 기준 + 여유). */
    private static final int HEADER_LEN = 16;

    static {
        // 이미지
        put("jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        put("jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        put("png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        put("gif", "GIF87a".getBytes(), "GIF89a".getBytes());
        put("bmp", new byte[] {'B', 'M'});
        // webp: RIFF....WEBP — 앞 4바이트만 확인(오프셋 8의 WEBP 는 별도 검사)
        put("webp", "RIFF".getBytes());
        // 문서
        put("pdf", "%PDF".getBytes());
        // ZIP 컨테이너 계열(docx·xlsx·pptx·hwpx·zip) — 모두 PK.. 로 시작
        byte[] zip = new byte[] {'P', 'K', 0x03, 0x04};
        byte[] zipEmpty = new byte[] {'P', 'K', 0x05, 0x06};
        byte[] zipSpanned = new byte[] {'P', 'K', 0x07, 0x08};
        put("zip", zip, zipEmpty, zipSpanned);
        put("docx", zip);
        put("xlsx", zip);
        put("pptx", zip);
        put("hwpx", zip);
        // 옛 MS Office·HWP 는 OLE 복합문서(D0CF11E0A1B11AE1)
        byte[] ole = new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        put("doc", ole);
        put("xls", ole);
        put("ppt", ole);
        put("hwp", ole);
    }

    private static void put(String ext, byte[]... sigs) {
        SIGNATURES.put(ext, Arrays.asList(sigs));
    }

    private FileSignature() {
    }

    /** 이 확장자가 시그니처 검사 대상인지(모르는 확장자는 검사하지 않는다). */
    public static boolean isCheckable(String ext) {
        return ext != null && SIGNATURES.containsKey(ext.toLowerCase());
    }

    /**
     * 확장자와 내용이 일치하는지. 검사 대상이 아니거나 파일이 비어 있으면 true(통과).
     * 읽기 실패는 판단할 수 없으니 거부한다(모르면 막는 쪽이 안전).
     */
    public static boolean matches(MultipartFile file, String ext) {
        if (!isCheckable(ext) || file == null || file.isEmpty()) {
            return true;
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(HEADER_LEN);
        } catch (IOException e) {
            return false;
        }
        for (byte[] sig : SIGNATURES.get(ext.toLowerCase())) {
            if (startsWith(head, sig)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
