import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Result, Spin } from 'antd'
import { boardApi } from '../adm/board/board.api'
import type { Board } from '../adm/board/board.api'
import StandardBoard from './boards/StandardBoard'
import FaqBoard from './boards/FaqBoard'

/**
 * 사용자 게시판 진입점(/gen/board/:boardId) — 게시판 유형(type_cd)에 따라 스킨 분기.
 *   FAQ(BOARD02) → 아코디언, 그 외(일반/갤러리/1:1) → 표준 스킨.
 * board.rowId로 스킨을 재마운트해 게시판 전환 시 상태가 섞이지 않도록 함.
 * 로드 실패(접근권 없음/미존재)는 빈 화면 대신 안내를 표시한다.
 */
export default function GenBoard() {
  const { boardId } = useParams()
  const [board, setBoard] = useState<Board | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'error'>('loading')

  useEffect(() => {
    setStatus('loading')
    setBoard(null)
    if (!boardId) { setStatus('error'); return }
    boardApi
      .view(boardId)
      .then((b) => { setBoard(b); setStatus('ok') })
      .catch(() => setStatus('error'))
  }, [boardId])

  if (status === 'loading') {
    return <div style={{ textAlign: 'center', padding: '80px 0' }}><Spin size="large" /></div>
  }
  if (status === 'error' || !board) {
    return (
      <Result
        status="warning"
        title="게시판을 불러올 수 없습니다."
        subTitle="접근 권한이 없거나 존재하지 않는 게시판입니다."
      />
    )
  }

  if (board.typeCd === 'BOARD02') return <FaqBoard key={board.rowId} board={board} />
  return <StandardBoard key={board.rowId} board={board} />
}
