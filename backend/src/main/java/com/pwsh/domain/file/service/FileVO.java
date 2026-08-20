package com.pwsh.domain.file.service;

import com.pwsh.common.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 파일 VO (file + file_ref 매핑 필드). */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileVO extends BaseVO {

    private String fileId;
    private String path;
    private String storedName; // 저장 파일명
    private String originalName; // 원본 파일명
    private String size;
    private String ext;
    private String description;

    // file_ref 매핑용 (엔티티 연결)
    private String mapKey;   // 연결 대상 PK
    private String fileType;  // 위치 구분 (예: POST/POST_IMG/POST_EDITOR/POPUP)
    private String sortNo;
    private String[] fileIds;   // 매핑 저장 시 파일 ID 목록
    private String[] descriptions; // 매핑 저장 시 파일별 설명(캡션) — fileIds와 같은 순서(갤러리)
}
