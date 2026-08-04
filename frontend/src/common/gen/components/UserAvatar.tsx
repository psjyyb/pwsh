import type { MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { gen } from '../../../gen/theme'

/**
 * 회원 아바타(프로필 이미지 or 닉네임 이니셜) + 선택적 이름. 게시글/댓글/모집 작성자 표시 공용.
 * handle(공개 식별자)을 주면 클릭 시 해당 회원 공개 프로필(/gen/user/:handle)로 이동한다.
 * ※ 로그인 ID는 클라이언트로 내려오지 않으므로 링크 키는 항상 handle이다.
 */
export default function UserAvatar({
  fileId,
  name,
  size = 26,
  showName = true,
  handle,
}: {
  fileId?: string
  name?: string
  size?: number
  showName?: boolean
  handle?: string
}) {
  const navigate = useNavigate()
  const clickable = !!handle
  const goProfile = clickable
    ? (e: MouseEvent) => { e.stopPropagation(); navigate(`/gen/user/${handle}`) }
    : undefined
  const avatar = (
    <span
      style={{
        width: size, height: size, borderRadius: '50%', background: gen.primary, color: '#fff',
        fontSize: Math.round(size * 0.42), fontWeight: 700, display: 'inline-flex',
        alignItems: 'center', justifyContent: 'center', overflow: 'hidden', flexShrink: 0,
      }}
    >
      {fileId
        ? <img src={`/api/pub/image/${fileId}`} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        : (name || '?').slice(0, 1)}
    </span>
  )
  if (!showName) {
    return clickable
      ? <span onClick={goProfile} title="프로필 보기" style={{ cursor: 'pointer', display: 'inline-flex' }}>{avatar}</span>
      : avatar
  }
  return (
    <span
      onClick={goProfile}
      title={clickable ? '프로필 보기' : undefined}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, minWidth: 0, cursor: clickable ? 'pointer' : undefined }}
    >
      {avatar}
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: clickable ? gen.primary : undefined }}>{name || '-'}</span>
    </span>
  )
}
