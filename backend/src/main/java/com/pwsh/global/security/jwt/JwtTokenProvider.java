package com.pwsh.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 토큰 생성·검증. (standard-template-spec.md 5)
 * secret이 짧아도 SHA-256으로 256비트 키를 만들어 HS256 요건을 충족한다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-ms}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms}") long refreshTokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(sha256(secret));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    /** Access Token 생성 (subject=memberId, claim typeCd=회원유형, typ=access, ver=토큰버전) */
    public String createAccessToken(String memberId, String typeCd, String tokenVer) {
        return build(memberId, typeCd, "access", tokenVer, accessTokenValidityMs);
    }

    /** Refresh Token 생성 (subject=memberId, typ=refresh, ver=토큰버전) */
    public String createRefreshToken(String memberId, String tokenVer) {
        return build(memberId, null, "refresh", tokenVer, refreshTokenValidityMs);
    }

    private String build(String subject, String typeCd, String typ, String tokenVer, long validityMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        var builder = Jwts.builder()
                .subject(subject)
                .claim("typ", typ) // access/refresh 구분 — refresh를 access로 오용 방지
                .claim("ver", tokenVer) // 토큰 버전 — member.token_ver와 대조(단일세션·로그아웃 무효화)
                .issuedAt(now)
                .expiration(expiry);
        if (typeCd != null) {
            builder.claim("typeCd", typeCd);
        }
        return builder.signWith(key).compact();
    }

    /** 토큰 종류(typ: access/refresh) 추출 */
    public String getType(String token) {
        return parse(token).get("typ", String.class);
    }

    /** 토큰 버전(ver) 추출. 구버전 토큰(ver 없음)은 null. */
    public String getVer(String token) {
        return parse(token).get("ver", String.class);
    }

    /** 토큰에서 memberId(subject) 추출 */
    public String getMemberId(String token) {
        return parse(token).getSubject();
    }

    /** 토큰에서 typeCd 클레임 추출 */
    public String getTypeCd(String token) {
        return parse(token).get("typeCd", String.class);
    }

    /** 유효성 검증 (서명·만료) */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private static byte[] sha256(String source) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
