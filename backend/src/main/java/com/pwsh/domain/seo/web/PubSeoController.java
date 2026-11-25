package com.pwsh.domain.seo.web;

import com.pwsh.common.util.Validate;
import com.pwsh.domain.seo.service.SeoService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RSS · sitemap.xml · robots.txt(CMS 1.2.43) — 로그인 없이 GET으로 읽는 공개 출력이라 5메서드 규약 밖이다(공개 이미지와 같다).
 * {@code /api/pub/**}는 SecurityConfig permitAll이고 메뉴 권한 검사 대상이 아니다 — 노출 범위는 {@link SeoService}가 책임진다.
 *
 * <p>사이트 루트의 {@code /sitemap.xml}·{@code /robots.txt}는 앞단이 이 주소로 넘긴다(개발은 vite.config.ts 프록시, 운영은 nginx 등 설정).
 * <p>Content-Type은 응답에 직접 싣는다 — 매핑의 produces로 고정하면 오류 응답(JSON)을 쓰지 못해 400이 406·500으로 바뀐다.
 */
@RestController
@RequestMapping("/api/pub/seo")
@RequiredArgsConstructor
public class PubSeoController {

    /** 리더·검색엔진이 자주 긁어 가므로 잠깐 캐시하게 둔다(게시판 공개 범위를 바꾸면 이만큼 늦게 반영된다) */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final SeoService seoService;

    @GetMapping("/rss/{boardId}")
    public ResponseEntity<String> rss(@PathVariable("boardId") String boardId) {
        Validate.numeric(boardId, "게시판");
        return xml(seoService.rss(boardId), "application/rss+xml");
    }

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return xml(seoService.sitemap(), "application/xml");
    }

    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok().cacheControl(CACHE)
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(seoService.robots());
    }

    /** null이면 404(사이트 주소 없음·비공개 게시판 등 — 왜 없는지는 알려 주지 않는다) */
    private static ResponseEntity<String> xml(String body, String type) {
        if (body == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CACHE)
                .contentType(MediaType.parseMediaType(type + ";charset=UTF-8"))
                .body(body);
    }
}
