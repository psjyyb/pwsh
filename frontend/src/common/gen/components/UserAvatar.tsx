import { gen } from '../../../gen/theme'

/** 회원 아바타(프로필 이미지 or 닉네임 이니셜) + 선택적 이름. 게시글/댓글/모집 작성자 표시 공용. */
export default function UserAvatar({
  fileId,
  name,
  size = 26,
  showName = true,
}: {
  fileId?: string
  name?: string
  size?: number
  showName?: boolean
}) {
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
  if (!showName) return avatar
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
      {avatar}
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{name || '-'}</span>
    </span>
  )
}
