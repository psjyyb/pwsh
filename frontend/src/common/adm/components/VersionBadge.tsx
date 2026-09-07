import { useEffect, useState } from 'react'
import { Tooltip } from 'antd'
import { apiPost } from '../../../api/http'

interface VersionInfo {
  version: string
  cmsVersion: string
  buildTime: string
}

/**
 * 사이드바 하단 버전 표시.
 *
 * <p>두 값을 같이 보여준다.
 * - version    : 이 프로젝트 버전
 * - cmsVersion : 바탕이 되는 CMS(틀) 버전. 이 틀 자체에서는 둘이 같아서 CMS 표기를 생략하고,
 *   틀을 복사해 만든 파생 프로젝트에서는 값이 달라지므로 "CMS x.y.z"를 함께 찍는다.
 *   → 파생 프로젝트가 CMS의 어느 시점까지 흡수했는지 화면에서 바로 확인할 수 있다.
 *   (CMS의 최신 버전이 무엇인지는 서버가 알 수 없다. 비교는 틀 저장소의 CHANGELOG.md로 한다)
 */
export default function VersionBadge({ collapsed }: { collapsed?: boolean }) {
  const [info, setInfo] = useState<VersionInfo | null>(null)

  useEffect(() => {
    // 버전 조회 실패는 화면에 영향을 주지 않는다 — 표시를 생략할 뿐
    apiPost<VersionInfo>('/pub/version', {})
      .then(setInfo)
      .catch(() => setInfo(null))
  }, [])

  if (!info || collapsed) {
    return null
  }
  const sameAsCms = info.cmsVersion === info.version
  const label = sameAsCms ? `v${info.version}` : `v${info.version} · CMS ${info.cmsVersion}`
  const tip = sameAsCms
    ? `빌드 ${info.buildTime || '-'}`
    : `이 서비스 v${info.version} / 바탕 CMS ${info.cmsVersion}\n빌드 ${info.buildTime || '-'}`

  return (
    <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{tip}</span>} placement="right">
      <div
        style={{
          padding: '8px 16px',
          fontSize: 12,
          color: '#999',
          borderTop: '1px solid #f0f0f0',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {label}
      </div>
    </Tooltip>
  )
}
