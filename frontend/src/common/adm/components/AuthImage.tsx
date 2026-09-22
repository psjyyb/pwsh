import { useEffect, useState } from 'react'
import { fileApi } from '../../../api/file'

interface Props {
  fileId?: string
  alt?: string
  /** 썸네일 한 변(px). 정사각 박스에 contain으로 맞춘다 */
  size?: number
}

/**
 * 인증이 필요한 이미지 썸네일.
 *
 * <p>⚠ `<img src="/api/pub/image/{id}">`로는 못 띄운다. 그 경로는 파일이 연결된 콘텐츠의 접근권으로
 * 서빙 가부를 판정하는데(IDOR 차단), 라이브러리에만 담긴 파일은 공개 연결이 없어 익명 요청이 403이다.
 * `<img>` 태그는 Authorization 헤더를 못 싣기 때문에 관리자라도 그대로는 실패한다.
 * 그래서 axios로 blob을 받아 object URL로 띄운다 — 대신 다 쓰면 revoke 해야 메모리가 샌다.
 */
export default function AuthImage({ fileId, alt = '', size = 120 }: Props) {
  const [url, setUrl] = useState('')
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    let created = ''
    setUrl('')
    setFailed(false)
    if (fileId) {
      fileApi
        .imageUrl(fileId)
        .then((u) => {
          if (alive) {
            created = u
            setUrl(u)
          } else {
            URL.revokeObjectURL(u) // 이미 언마운트됨 — 만들자마자 반납
          }
        })
        .catch(() => alive && setFailed(true))
    }
    return () => {
      alive = false
      if (created) URL.revokeObjectURL(created)
    }
  }, [fileId])

  const box: React.CSSProperties = {
    width: size,
    height: size,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#fafafa',
    border: '1px solid #f0f0f0',
    borderRadius: 6,
    overflow: 'hidden',
    color: '#bbb',
    fontSize: 12,
  }

  if (failed) return <div style={box}>미리보기 없음</div>
  if (!url) return <div style={box} />
  return (
    <div style={box}>
      <img src={url} alt={alt} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
    </div>
  )
}
