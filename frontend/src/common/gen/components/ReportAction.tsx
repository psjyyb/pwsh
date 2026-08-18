import { useState } from 'react'
import { Input, Modal, message } from 'antd'
import { reportApi } from '../../../api/report'
import CodeSelect from '../../adm/components/CodeSelect'

/** 콘텐츠 신고 링크 + 사유 모달(분류 선택 + 상세). 게시글/댓글/모집에 재사용. 로그인 사용자에게만 노출 권장. */
export default function ReportAction({ targetType, targetId }: { targetType: 'BBS' | 'COMMENT' | 'RECRUIT' | 'CHAT'; targetId: string }) {
  const [open, setOpen] = useState(false)
  const [reasonCd, setReasonCd] = useState<string | undefined>()
  const [reason, setReason] = useState('')

  const submit = async () => {
    if (!reasonCd) { message.warning('신고 사유를 선택하세요.'); return }
    if (!reason.trim()) { message.warning('신고 내용을 입력하세요.'); return }
    try {
      await reportApi.report(targetType, targetId, reason.trim(), reasonCd)
      message.success('신고가 접수되었습니다.')
      setOpen(false)
      setReason('')
      setReasonCd(undefined)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '신고 실패')
    }
  }

  return (
    <>
      <a style={{ color: '#bbb', fontSize: 12 }} onClick={() => { setReason(''); setReasonCd(undefined); setOpen(true) }}>신고</a>
      <Modal title="신고하기" open={open} onCancel={() => setOpen(false)} onOk={submit} okText="신고" cancelText="취소" okButtonProps={{ danger: true }}>
        <p style={{ color: '#888', fontSize: 13 }}>부적절한 콘텐츠를 신고합니다. 사유를 선택하고 내용을 적어주세요.</p>
        <CodeSelect
          pCodeId="REPORT00" value={reasonCd} onChange={setReasonCd}
          placeholder="신고 사유 선택" style={{ width: '100%', marginBottom: 10 }}
        />
        <Input.TextArea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500}
          autoSize={{ minRows: 3, maxRows: 6 }} placeholder="구체적인 신고 내용 (최대 500자)" />
      </Modal>
    </>
  )
}
