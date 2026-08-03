import { useState } from 'react'
import { Input, Modal, message } from 'antd'
import { reportApi } from '../../../api/report'

/** 콘텐츠 신고 링크 + 사유 모달. 게시글/댓글/모집에 재사용. 로그인 사용자에게만 노출 권장. */
export default function ReportAction({ targetType, targetId }: { targetType: 'BBS' | 'COMMENT' | 'RECRUIT'; targetId: string }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')

  const submit = async () => {
    if (!reason.trim()) { message.warning('신고 사유를 입력하세요.'); return }
    try {
      await reportApi.report(targetType, targetId, reason.trim())
      message.success('신고가 접수되었습니다.')
      setOpen(false)
      setReason('')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '신고 실패')
    }
  }

  return (
    <>
      <a style={{ color: '#bbb', fontSize: 12 }} onClick={() => { setReason(''); setOpen(true) }}>신고</a>
      <Modal title="신고하기" open={open} onCancel={() => setOpen(false)} onOk={submit} okText="신고" cancelText="취소" okButtonProps={{ danger: true }}>
        <p style={{ color: '#888', fontSize: 13 }}>부적절한 콘텐츠를 신고합니다. 사유를 적어주세요.</p>
        <Input.TextArea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500}
          autoSize={{ minRows: 3, maxRows: 6 }} placeholder="신고 사유 (최대 500자)" />
      </Modal>
    </>
  )
}
