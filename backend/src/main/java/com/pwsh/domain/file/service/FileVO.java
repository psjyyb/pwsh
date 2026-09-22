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

    // 미디어 라이브러리(조회 전용 계산값)
    /**
     * 이 파일을 쓰는 곳 수 — LIBRARY 매핑은 "소속" 표시라 세지 않는다.
     * ★ file_ref뿐 아니라 member.profile_file_id(직접 참조)도 함께 센다. 안 세면 프로필 사진이
     * '미사용'으로 보여 관리자가 지우고, 그 회원 프로필이 깨진다.
     */
    private String refCnt;
    /** 'Y'=라이브러리에 담긴 파일(엔티티에 안 붙어도 GC가 지우지 않는다) */
    private String libraryYn;
    /** 사용처 목록에서 대상을 사람이 알아볼 이름(게시글 제목·회원 닉네임 등). 못 찾으면 null */
    private String targetName;

    // 미디어 라이브러리 목록 필터
    /** 확장자 묶음: IMAGE=이미지만 / ETC=이미지 외. 빈 값이면 전체 */
    private String filterExtGroup;
    /** 사용여부: USED=어딘가 쓰이는 중 / UNUSED=아무 데도 안 쓰임. 빈 값이면 전체 */
    private String filterUsed;
    /** 'Y'면 라이브러리에 담긴 것만(선택기 모달이 쓴다) */
    private String filterLibrary;
    /** 등록일 범위(YYYY-MM-DD) */
    private String filterFrom;
    private String filterTo;
}
