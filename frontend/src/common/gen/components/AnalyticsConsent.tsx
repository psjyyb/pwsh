import type { AnalyticsIds } from '../../util/analytics'

/**
 * 분석 쿠키 동의 배너(CMS 1.2.44) — 분석 도구 ID가 있고 방문자가 아직 고르지 않았을 때(또는 푸터 '쿠키 설정'으로 다시 열었을 때) 하단에 뜬다.
 * 동의 전에는 분석 스크립트를 불러오지 않으므로 이 배너가 곧 스위치다. 거부해도 이용에 제한은 없다.
 * 화면을 가리지 않는 띠로 둔다(모달로 막으면 첫 화면부터 이용을 강요하는 꼴이 된다).
 */
export default function AnalyticsConsent({
  ids,
  onAccept,
  onReject,
}: {
  ids: AnalyticsIds
  onAccept: () => void
  onReject: () => void
}) {
  const tools = [ids.ga4Id && 'Google Analytics', ids.naverId && '네이버 애널리틱스'].filter(Boolean).join('·')
  return (
    <div className="gen-consent" role="region" aria-label="쿠키 사용 동의">
      <p>
        방문 통계({tools})를 위해 쿠키를 사용합니다. 동의하면 본 화면과 이용 환경(브라우저·기기 등)이 분석 서비스로 전송되며,
        동의하지 않아도 이용에 제한은 없습니다. 선택은 화면 맨 아래 &lsquo;쿠키 설정&rsquo;에서 언제든 바꿀 수 있습니다.
      </p>
      <div className="gen-consent-btns">
        <button type="button" onClick={onReject}>
          거부
        </button>
        <button type="button" className="is-primary" onClick={onAccept}>
          동의
        </button>
      </div>
    </div>
  )
}
