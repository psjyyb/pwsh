package com.pwsh.common;

import java.io.Serializable;
import lombok.Data;

/**
 * 공통 VO 최상위 부모. 모든 도메인 VO가 상속. (표준 CMS BaseVO 계승 + camelCase/간소화)
 * - 페이징: pageIndex(1-base), size → offset 계산
 * - 검색: searchCondition/searchKeyword
 * - 공통키: dbKey/dbKeys (조회 결과 PK 별칭 + 단건 조회/수정/삭제 WHERE 바인딩 겸용)
 * - audit: regId/updId/regDt/updDt/regIp/updIp, useYn (AuditInterceptor가 자동 세팅)
 */
@Data
public class BaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 페이징 =====
    /** 페이지 번호(1-base) */
    private int pageIndex = 1;
    /** 페이지당 목록 수 */
    private int size = 10;

    /** LIMIT/OFFSET 계산용 (매퍼에서 #{offset}) */
    public int getOffset() {
        return (pageIndex - 1) * size;
    }

    // ===== 검색 =====
    private String searchCondition;
    private String searchKeyword;

    // ===== 개인정보 대칭키(pgcrypto AES). 매퍼에서 #{encKey}로 사용 =====
    // 운영은 환경변수 DB_ENC_KEY로 주입(미설정 시 기본값). 표준 CMS의 BaseVO enc_key 계승 방식.
    private static final String ENC_KEY = System.getenv().getOrDefault("DB_ENC_KEY", "psjyyb");

    /** MyBatis #{encKey} 바인딩용 (모든 VO 공통). 읽기 전용이라 로그/응답 노출 없음 */
    public String getEncKey() {
        return ENC_KEY;
    }

    // ===== 공통키(단건/다건 선택) =====
    /** 해당 화면 주체 테이블의 자기 PK. 조회 결과 별칭(AS db_key) + 단건 조회/수정/삭제 WHERE 바인딩 겸용 */
    private String dbKey;
    private String[] dbKeys;

    // ===== audit (AuditInterceptor 자동 세팅) =====
    private String regId;
    private String updId;
    private String regDt;
    private String updDt;
    private String regIp;
    private String updIp;
    private String useYn;
}
