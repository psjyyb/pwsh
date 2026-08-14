package com.pwsh.common;

import java.io.Serializable;
import lombok.Data;

/**
 * 공통 VO 최상위 부모. 모든 도메인 VO가 상속(필드는 String 통일, DB snake_case ↔ camelCase 자동 매핑).
 * - 페이징: pageNo(1-base), pageSize → offset 계산
 * - 검색: filterField/filterKeyword
 * - 공통키: rowId/rowIds (조회 결과 PK 별칭 + 단건 조회/수정/삭제 WHERE 바인딩 겸용)
 * - audit: regId/updId/regDt/updDt/regIp/updIp, useYn (AuditInterceptor가 자동 세팅)
 */
@Data
public class BaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 페이징 =====
    /** 페이지 번호(1-base) */
    private int pageNo = 1;
    /** 페이지당 목록 수 */
    private int pageSize = 10;

    /** LIMIT/OFFSET 계산용 (매퍼에서 #{offset}) */
    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }

    // ===== 목록 필터 =====
    /** 검색 대상 컬럼(매퍼에서 분기). 통합검색의 keyword와 구분하려고 filter 접두어를 쓴다 */
    private String filterField;
    private String filterKeyword;

    /** 정렬 위/아래 교환 방향(UP/DOWN). 목록 필터 필드를 재활용하지 않으려고 따로 둔다 */
    private String direction;

    // ===== 개인정보 대칭키(pgcrypto AES). 매퍼에서 #{cryptoKey}로 사용 =====
    // 운영은 환경변수 APP_CRYPTO_KEY로 주입(미설정 시 기본값). VO 공통 필드라 모든 매퍼가 같은 키를 쓴다.
    private static final String CRYPTO_KEY = System.getenv().getOrDefault("APP_CRYPTO_KEY", "psjyyb");

    /**
     * MyBatis #{cryptoKey} 바인딩용 (모든 VO 공통).
     * ★ @JsonIgnore 필수 — 이 게터가 직렬화되면 DB 개인정보 암호화 키가 API 응답으로 유출된다
     *   (VO가 그대로 응답 body에 실리는 구조이고 공개 조회 엔드포인트도 있음).
     *   MyBatis는 리플렉션으로 게터를 읽으므로 Jackson 제외는 파라미터 바인딩에 영향 없음.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCryptoKey() {
        return CRYPTO_KEY;
    }

    // ===== 공통키(단건/다건 선택) =====
    /** 해당 화면 주체 테이블의 자기 PK. 조회 결과 별칭(AS row_id) + 단건 조회/수정/삭제 WHERE 바인딩 겸용 */
    private String rowId;
    private String[] rowIds;

    // ===== audit (AuditInterceptor 자동 세팅) =====
    private String regId;
    private String updId;
    private String regDt;
    private String updDt;
    private String regIp;
    private String updIp;
    private String useYn;
}
