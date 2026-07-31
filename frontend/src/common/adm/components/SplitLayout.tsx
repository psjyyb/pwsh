import type { ReactNode } from 'react'
import { Col, Row } from 'antd'

/**
 * 분할 마스터-디테일 레이아웃 (관리자 표준 폼 방식).
 * 좌: 목록, 우: 상세·등록·수정. 모바일(<md)에서는 세로 스택.
 *   <SplitLayout list={<Card.../>} detail={<Card.../>} />
 */
export default function SplitLayout({ list, detail }: { list: ReactNode; detail: ReactNode }) {
  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} md={12}>
        {list}
      </Col>
      <Col xs={24} md={12}>
        {detail}
      </Col>
    </Row>
  )
}
