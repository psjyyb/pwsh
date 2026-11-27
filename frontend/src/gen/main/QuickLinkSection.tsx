import { useEffect, useState } from 'react'
import type { MouseEvent } from 'react'
import MenuGlyph from '../../common/adm/components/MenuGlyph'
import { isInternalQuickLink, isSafeQuickLinkUrl, quickLinkApi } from '../../adm/quicklink/quicklink.api'
import type { QuickLink } from '../../adm/quicklink/quicklink.api'
import Reveal from './Reveal'

/**
 * 자주찾는 서비스(CMS 1.2.45) — 메인 히어로 바로 아래 바로가기 버튼. 관리자 > 자주찾는 서비스에서 정한다.
 *
 * <p>노출된 것이 하나도 없거나 조회에 실패하면 섹션을 렌더하지 않는다(빈 띠가 남지 않게).
 * <p>★ 주소는 서버가 저장 때 검사하지만, DB를 직접 고친 값이 링크가 되지 않도록 여기서 같은 규칙(isSafeQuickLinkUrl)으로 한 번 더 거른다.
 * 링크는 a 태그로 둔다(가운데 클릭·새 탭 열기가 되게) — 사이트 안 주소를 현재 창으로 열 때만 라우터로 이동한다.
 */
export default function QuickLinkSection({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [links, setLinks] = useState<QuickLink[]>([])

  useEffect(() => {
    quickLinkApi
      .mainList()
      .then((list) => setLinks(list.filter((l) => l.name && isSafeQuickLinkUrl(l.linkUrl))))
      .catch(() => setLinks([]))
  }, [])

  if (links.length === 0) return null

  const click = (e: MouseEvent<HTMLAnchorElement>, l: QuickLink) => {
    // 새 창·외부 주소·보조키(새 탭) 클릭은 브라우저에 맡긴다
    if (l.targetYn === 'Y' || !isInternalQuickLink(l.linkUrl!) || e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return
    e.preventDefault()
    onNavigate(l.linkUrl!)
  }

  return (
    <section className="gen-quick" aria-labelledby="gen-quick-title">
      <div className="gen-wfix">
        <h2 id="gen-quick-title" className="gen-quick-title">자주찾는 서비스</h2>
        <Reveal as="ul" dir="fade" className="gen-quick-list">
          {links.map((l) => (
            <li key={l.rowId}>
              <a
                className="gen-quick-item"
                href={l.linkUrl}
                onClick={(e) => click(e, l)}
                {...(l.targetYn === 'Y' ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
              >
                <span className="gen-quick-ico">
                  <MenuGlyph name={l.icon} size={24} />
                </span>
                <span className="gen-quick-name">{l.name}</span>
              </a>
            </li>
          ))}
        </Reveal>
      </div>
    </section>
  )
}
