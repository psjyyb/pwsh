import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { bbsinfoApi } from '../adm/bbsinfo/bbsinfo.api'
import type { Bbsinfo } from '../adm/bbsinfo/bbsinfo.api'
import StandardBoard from './boards/StandardBoard'
import FaqBoard from './boards/FaqBoard'

/**
 * 사용자 게시판 진입점(/gen/board/:bbsinfoId) — 게시판 유형(bbsinfo_cd)에 따라 스킨 분기.
 *   FAQ(BBSINFO002) → 아코디언, 그 외(일반/갤러리/1:1) → 표준 스킨.
 * board.dbKey로 스킨을 재마운트해 게시판 전환 시 상태가 섞이지 않도록 함.
 */
export default function GenBoard() {
  const { bbsinfoId } = useParams()
  const [board, setBoard] = useState<Bbsinfo | null>(null)

  useEffect(() => {
    setBoard(null)
    if (bbsinfoId) bbsinfoApi.view(bbsinfoId).then(setBoard).catch(() => setBoard(null))
  }, [bbsinfoId])

  if (!board) return null

  if (board.bbsinfoCd === 'BBSINFO002') return <FaqBoard key={board.dbKey} board={board} />
  return <StandardBoard key={board.dbKey} board={board} />
}
