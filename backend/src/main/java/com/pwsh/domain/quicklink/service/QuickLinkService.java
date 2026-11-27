package com.pwsh.domain.quicklink.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.common.message.Messages;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자주찾는 서비스(CMS 1.2.45, 단일 @Service) — 사용자 메인 히어로 아래 바로가기 버튼.
 *
 * <p>★ 주소는 메인에서 그대로 링크(href)가 된다. {@code javascript:}가 들어가면 누르는 방문자 화면에서 스크립트가 돌고,
 * {@code //다른사이트}·{@code /\다른사이트}는 브라우저가 외부 주소로 읽어 사이트 버튼이 피싱 링크가 된다 —
 * 그래서 '/'로 시작하는 사이트 안 경로와 http(s)://만 받는다(화면도 같은 규칙으로 한 번 더 거른다).
 */
@Service
@RequiredArgsConstructor
public class QuickLinkService {

    static final int NAME_MAX = 50;
    static final int URL_MAX = 255;
    /**
     * 사이트 안 경로 — '/' 다음이 '/'·'\'면 브라우저가 다른 호스트로 읽는다. 공백·제어문자 없음.
     * 공백은 유니코드 기준(전각 공백 등 포함)으로 본다 — 화면(quicklink.api.ts)의 JS 정규식과 판정이 같아야 한다
     * (어긋나면 서버는 받았는데 메인에서 조용히 빠진다).
     */
    private static final Pattern INTERNAL = Pattern.compile("^/(?![/\\\\])[^\\s\\p{Cc}]*$", Pattern.UNICODE_CHARACTER_CLASS);
    /** 외부 주소 — 호스트가 있어야 한다 */
    private static final Pattern EXTERNAL = Pattern.compile("^https?://[^\\s\\p{Cc}/?#\\\\]+([/?#][^\\s\\p{Cc}]*)?$",
            Pattern.UNICODE_CHARACTER_CLASS);
    /** 아이콘 키(MenuGlyph 레지스트리 키 형식) — 없는 키는 화면이 기본 그림으로 그린다 */
    private static final Pattern ICON = Pattern.compile("^[a-z0-9-]{1,30}$");

    private final CommonDAO commonDAO;

    public List<QuickLinkVO> selectList(QuickLinkVO vo) {
        return commonDAO.selectList("quickLinkDAO.selectList", vo);
    }

    public int selectListTotalCount(QuickLinkVO vo) {
        return commonDAO.selectOne("quickLinkDAO.selectListTotalCount", vo);
    }

    /** 사용자 메인 노출용(삭제 안 됨 + 노출). 비로그인도 읽는다 */
    public List<QuickLinkVO> selectMainList() {
        return commonDAO.selectList("quickLinkDAO.selectMainList", new QuickLinkVO());
    }

    public QuickLinkVO selectView(QuickLinkVO vo) {
        return commonDAO.selectOne("quickLinkDAO.selectView", vo);
    }

    /** 등록 — 맨 뒤 순서. 새 ID를 돌려준다 */
    @Transactional
    public String insert(QuickLinkVO vo) {
        normalize(vo);
        commonDAO.insert("quickLinkDAO.insert", vo);
        return vo.getRowId();
    }

    @Transactional
    public void update(QuickLinkVO vo) {
        normalize(vo);
        commonDAO.update("quickLinkDAO.update", vo);
    }

    /** 인접 행과 순서 교환 — 임시값(-1)으로 3단계(배너·팝업·메뉴와 같은 방식). 맨 끝이면 할 일이 없다 */
    @Transactional
    public void swapSort(QuickLinkVO vo) {
        QuickLinkVO cur = commonDAO.selectOne("quickLinkDAO.selectView", vo);
        if (cur == null) {
            return;
        }
        cur.setDirection(vo.getDirection());
        QuickLinkVO adj = commonDAO.selectOne("quickLinkDAO.selectAdjacentSort", cur);
        if (adj == null) {
            return;
        }
        setSort(cur.getRowId(), "-1");
        setSort(adj.getRowId(), cur.getSortNo());
        setSort(cur.getRowId(), adj.getSortNo());
    }

    /** 삭제(논리) + 뒤 순서 당김. 이미 지운 것을 또 지우면 당기지 않는다(뒤 순서가 두 번 당겨지면 자리가 겹친다) */
    @Transactional
    public void delete(QuickLinkVO vo) {
        if (commonDAO.update("quickLinkDAO.delete", vo) > 0) {
            commonDAO.update("quickLinkDAO.shiftSortAfterDelete", vo);
        }
    }

    private void setSort(String rowId, String sortNo) {
        QuickLinkVO v = new QuickLinkVO();
        v.setRowId(rowId);
        v.setSortNo(sortNo);
        commonDAO.update("quickLinkDAO.updateSort", v);
    }

    /** 앞뒤 공백 제거 + 길이·주소·아이콘 검사. 열기·노출은 Y/N이 아니면 기본값(현재 창·노출) */
    static void normalize(QuickLinkVO vo) {
        String name = trim(vo.getName());
        String url = trim(vo.getLinkUrl());
        String icon = trim(vo.getIcon());
        if (name.isEmpty() || url.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.quickLink.required"));
        }
        if (name.length() > NAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.quickLink.nameTooLong", NAME_MAX));
        }
        if (url.length() > URL_MAX || !(INTERNAL.matcher(url).matches() || EXTERNAL.matcher(url).matches())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.quickLink.url", URL_MAX));
        }
        if (!icon.isEmpty() && !ICON.matcher(icon).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, Messages.get("error.quickLink.icon"));
        }
        vo.setName(name);
        vo.setLinkUrl(url);
        vo.setIcon(icon.isEmpty() ? null : icon);
        vo.setTargetYn("Y".equals(vo.getTargetYn()) ? "Y" : "N");
        vo.setDisplayYn("N".equals(vo.getDisplayYn()) ? "N" : "Y");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
