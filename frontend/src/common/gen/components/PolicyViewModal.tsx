import { useEffect, useState } from 'react'
import { Modal, Spin } from 'antd'
import { policyApi } from '../../../adm/policy/policy.api'
import type { Policy } from '../../../adm/policy/policy.api'
import SafeHtml from '../../SafeHtml'

/**
 * 약관 본문 보기(공개) — 가입 화면의 동의 항목과 푸터 링크에서 함께 쓴다.
 * 목록(publicList)은 본문을 안 주므로 열 때 rowId로 본문을 따로 읽는다.
 */
export default function PolicyViewModal({
  rowId,
  onClose,
}: {
  rowId?: string
  onClose: () => void
}) {
  const [policy, setPolicy] = useState<Policy | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!rowId) {
      setPolicy(null)
      return
    }
    setLoading(true)
    policyApi
      .publicView(rowId)
      .then(setPolicy)
      .catch(() => setPolicy(null))
      .finally(() => setLoading(false))
  }, [rowId])

  return (
    <Modal
      open={!!rowId}
      title={policy?.title ?? '약관'}
      onCancel={onClose}
      footer={null}
      width={720}
      destroyOnHidden
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
      ) : policy?.content ? (
        // 본문은 관리자가 에디터로 저장한 HTML — SafeHtml이 스크립트 등을 제거한다
        <div style={{ maxHeight: '60vh', overflowY: 'auto', fontSize: 14, lineHeight: 1.7 }}>
          <SafeHtml html={policy.content} />
        </div>
      ) : (
        <div style={{ color: '#999', padding: 24, textAlign: 'center' }}>내용이 없습니다.</div>
      )}
    </Modal>
  )
}
