package com.pwsh.domain.seo.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.domain.config.service.ConfigVO;
import com.pwsh.domain.configitem.service.ConfigItemService;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * RSS · sitemap.xml · robots.txt(CMS 1.2.43, 단일 @Service) — 로그인 없이 읽히는 출력.
 *
 * <p>★ 링크의 사이트 주소는 확장설정 {@code site.url}만 쓴다(사용자 결정). 요청의 Host 헤더로 만들지 않는다 — 앞단 캐시가
 * 경로만으로 응답을 저장하면 조작된 Host가 박힌 링크가 다른 사람에게 나간다. 비었거나 형식이 틀리면 RSS·sitemap을 끈다(404, 설치 점검 경고).
 * <p>★ 대상은 비로그인이 사용자 화면에서 볼 수 있는 것만이다(seoDAO — 취미 게시판(커뮤니티 공개) 또는 GEN 메뉴 GUEST 권한, 비밀글·승인 전 글·삭제 글·답글 제외).
 * <p>글 링크는 이 저장소 화면 규칙대로 {@code ?post=}다(CMS는 {@code ?postId=}) — 다르게 쓰면 RSS·검색 결과에서 눌러도 목록만 열린다.
 * <p>XML은 문자열로 잇지 않고 {@link XMLStreamWriter}로 쓴다 — 제목의 {@code < &}를 손으로 이스케이프하다 한 곳만 빠져도 RSS가 통째로 깨진다.
 */
@Service
@RequiredArgsConstructor
public class SeoService {

    /** RSS 본문 요약 길이(사용자 결정: 제목 + 본문 앞부분) */
    static final int EXCERPT = 200;
    /** http(s)://호스트[:포트][/경로] — 질의문자열·조각은 받지 않는다 */
    private static final Pattern SITE_URL = Pattern.compile("^https?://[^\\s/?#]+(/[^\\s?#]*)?$");
    /** XML 1.0에 넣을 수 없는 글자(제어문자 등) — 본문에 섞여 있으면 RSS 리더가 파싱을 포기한다 */
    private static final Pattern XML_INVALID = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\uD7FF\\uE000-\\uFFFD\\x{10000}-\\x{10FFFF}]");
    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";

    private final CommonDAO commonDAO;
    private final ConfigItemService configItemService;

    /** 확장설정 사이트 주소 그대로(설치 점검용) */
    public String rawSiteUrl() {
        return configItemService.get("site.url", "").trim();
    }

    /** 링크에 쓸 사이트 주소 — 끝의 / 를 떼고, 형식이 맞을 때만. 비었거나 틀리면 null(RSS·sitemap을 끈다) */
    public String siteUrl() {
        String s = rawSiteUrl();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return SITE_URL.matcher(s).matches() ? s : null;
    }

    /** 이 게시판에 RSS가 있는가 — 게시판 화면이 RSS 링크를 띄울지 정한다 */
    public boolean rssAvailable(String boardId) {
        return siteUrl() != null && rssBoard(boardId) != null;
    }

    /** 게시판 RSS 2.0. 줄 수 없으면(사이트 주소 없음·비공개 게시판·1:1 문의·없는 게시판) null → 404 */
    public String rss(String boardId) {
        String site = siteUrl();
        SeoVO board = site == null ? null : rssBoard(boardId);
        if (board == null) {
            return null;
        }
        SeoVO key = new SeoVO();
        key.setBoardId(boardId);
        List<SeoVO> posts = commonDAO.selectList("seoDAO.selectRssItems", key);
        String boardLink = site + "/gen/board/" + board.getBoardId();
        return write(w -> {
            w.writeStartElement("rss");
            w.writeAttribute("version", "2.0");
            w.writeNamespace("dc", DC_NS);
            w.writeNamespace("atom", ATOM_NS);
            w.writeStartElement("channel");
            el(w, "title", siteTitle() + " - " + board.getBoardName());
            el(w, "link", boardLink);
            el(w, "description", blank(board.getDescription()) ? board.getBoardName() : board.getDescription());
            el(w, "language", "ko");
            w.writeEmptyElement("atom", "link", ATOM_NS);
            w.writeAttribute("href", site + "/api/pub/seo/rss/" + board.getBoardId());
            w.writeAttribute("rel", "self");
            w.writeAttribute("type", "application/rss+xml");
            if (!posts.isEmpty()) {
                el(w, "lastBuildDate", rfc1123(posts.get(0).getRegDt()));
            }
            for (SeoVO p : posts) {
                String link = boardLink + "?post=" + p.getRowId();
                w.writeStartElement("item");
                el(w, "title", p.getTitle());
                el(w, "link", link);
                w.writeStartElement("guid");
                w.writeAttribute("isPermaLink", "true");
                w.writeCharacters(link);
                w.writeEndElement();
                el(w, "pubDate", rfc1123(p.getRegDt()));
                if (!blank(p.getRegName())) {
                    w.writeStartElement("dc", "creator", DC_NS);
                    w.writeCharacters(clean(p.getRegName()));
                    w.writeEndElement();
                }
                el(w, "description", excerpt(p.getContent()));
                w.writeEndElement();
            }
            w.writeEndElement();
            w.writeEndElement();
        });
    }

    /** sitemap.xml — 비로그인 공개 메뉴 화면 + 취미 화면 + 모집 + 공개 게시판의 공개 원글. 사이트 주소가 없으면 null → 404 */
    public String sitemap() {
        String site = siteUrl();
        if (site == null) {
            return null;
        }
        // 주소 → lastmod(없으면 null). 메뉴가 같은 화면을 두 번 가리켜도 한 번만
        Map<String, String> urls = new LinkedHashMap<>();
        for (SeoVO m : commonDAO.<SeoVO>selectList("seoDAO.selectSitemapMenus", new SeoVO())) {
            String path = menuPath(m);
            if (path != null) {
                urls.putIfAbsent(site + path, null);
            }
        }
        // 취미 화면·모집 상세는 메뉴 없이 공개다(취미 카탈로그·모집 조회 API가 비로그인에게 열려 있다)
        for (SeoVO h : commonDAO.<SeoVO>selectList("seoDAO.selectSitemapHobbies", new SeoVO())) {
            urls.putIfAbsent(site + "/gen/hobby/" + h.getRowId(), h.getUpdDt());
        }
        for (SeoVO r : commonDAO.<SeoVO>selectList("seoDAO.selectSitemapRecruits", new SeoVO())) {
            urls.putIfAbsent(site + "/gen/recruit/" + r.getRowId(), r.getUpdDt());
        }
        for (SeoVO p : commonDAO.<SeoVO>selectList("seoDAO.selectSitemapPosts", new SeoVO())) {
            urls.putIfAbsent(site + "/gen/board/" + p.getBoardId() + "?post=" + p.getRowId(), p.getUpdDt());
        }
        return write(w -> {
            w.writeStartElement("urlset");
            w.writeDefaultNamespace(SITEMAP_NS);
            for (Map.Entry<String, String> u : urls.entrySet()) {
                w.writeStartElement("url");
                el(w, "loc", u.getKey());
                if (u.getValue() != null) {
                    el(w, "lastmod", u.getValue());
                }
                w.writeEndElement();
            }
            w.writeEndElement();
        });
    }

    /**
     * robots.txt — 관리자 화면·로그인·가입·비밀번호 찾기·검색 결과·개인 화면(마이페이지·쪽지·피드·내 취미·첫 설정)·API(공개 이미지·RSS 제외)는 수집하지 않게 하고,
     * 사이트 주소가 있으면 sitemap 위치를 알린다(사이트 루트의 /sitemap.xml — 앞단이 /api/pub/seo/sitemap.xml로 넘긴다).
     */
    public String robots() {
        StringBuilder sb = new StringBuilder()
                .append("User-agent: *\n")
                .append("Disallow: /adm\n")
                .append("Disallow: /login\n")
                .append("Disallow: /signup\n")
                .append("Disallow: /forgot\n")
                .append("Disallow: /gen/search\n")
                .append("Disallow: /gen/mypage\n")
                .append("Disallow: /gen/message\n")
                .append("Disallow: /gen/feed\n")
                .append("Disallow: /gen/myhobby\n")
                .append("Disallow: /gen/onboarding\n")
                .append("Disallow: /api/\n")
                .append("Allow: /api/pub/\n");
        String site = siteUrl();
        if (site != null) {
            sb.append("\nSitemap: ").append(site).append("/sitemap.xml\n");
        }
        return sb.toString();
    }

    /** 메뉴 → 사용자 화면 경로(프론트 GenLayout의 규칙과 같다). 주소형은 사이트 안쪽(/gen/...)만 — 외부 링크는 sitemap 대상이 아니다 */
    static String menuPath(SeoVO m) {
        if ("MENU02".equals(m.getConnCd())) {
            return "/gen/board/" + m.getConnId();
        }
        if ("MENU03".equals(m.getConnCd())) {
            return "/gen/page/" + m.getConnId();
        }
        if ("MENU05".equals(m.getConnCd())) {
            return "/gen/form/" + m.getConnId();
        }
        String link = m.getLinkUrl();
        return "MENU01".equals(m.getConnCd()) && link != null && link.startsWith("/gen/") ? link : null;
    }

    /** 본문 HTML → 태그·엔티티를 푼 평문 앞부분(넘치면 …). 글자(코드포인트) 단위로 자른다 — 이모지 반쪽이 남지 않게 */
    static String excerpt(String html) {
        String t = html == null ? "" : html.replaceAll("<[^>]*>", " ");
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&#x27;", "'").replace("&amp;", "&"); // &amp;는 마지막에(두 번 풀지 않게)
        t = t.replaceAll("\\s+", " ").trim();
        if (t.codePointCount(0, t.length()) <= EXCERPT) {
            return t;
        }
        return t.substring(0, t.offsetByCodePoints(0, EXCERPT)) + "…";
    }

    /** 'YYYY-MM-DDTHH:MI:SS'(DB가 서버 시간대로 저장한 값) → RFC 1123 */
    private static String rfc1123(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    private SeoVO rssBoard(String boardId) {
        if (boardId == null || !boardId.matches("\\d{1,9}")) {
            return null;
        }
        SeoVO key = new SeoVO();
        key.setBoardId(boardId);
        return commonDAO.selectOne("seoDAO.selectRssBoard", key);
    }

    private String siteTitle() {
        ConfigVO cfg = commonDAO.selectOne("configDAO.selectView", new ConfigVO());
        return cfg == null || blank(cfg.getTitle()) ? "" : cfg.getTitle();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String s) {
        return s == null ? "" : XML_INVALID.matcher(s).replaceAll("");
    }

    private static void el(XMLStreamWriter w, String name, String text) throws XMLStreamException {
        w.writeStartElement(name);
        w.writeCharacters(clean(text));
        w.writeEndElement();
    }

    @FunctionalInterface
    private interface XmlBody {
        void write(XMLStreamWriter w) throws XMLStreamException;
    }

    private static String write(XmlBody body) {
        try {
            StringWriter sw = new StringWriter();
            XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);
            w.writeStartDocument("UTF-8", "1.0");
            body.write(w);
            w.writeEndDocument();
            w.close();
            return sw.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("XML 생성 실패", e);
        }
    }
}
