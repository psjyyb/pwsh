import { useEffect, useState } from 'react'
import { Modal, Select, message } from 'antd'
import { authGroupApi } from '../authgroup/authgroup.api'
import { memberApi } from './member.api'
import type { Member } from './member.api'

interface Props {
  open: boolean
  Member: Member | null
  onClose: () => void
}

/** 사용자에게 권한그룹 지정 (다중 선택) */
export default function AuthGroupAssignModal({ open, Member, onClose }: Props) {
  const [options, setOptions] = useState<{ value: string; label: string }[]>([])
  const [selected, setSelected] = useState<string[]>([])

  useEffect(() => {
    if (!open || !Member) return
    authGroupApi
      .combo()
      .then((list) => setOptions(list.map((g) => ({ value: g.authGroupId!, label: `${g.authGroupName} (${g.authGroupId})` }))))
      .catch(() => setOptions([]))
    memberApi.getAuthGroups(Member.memberId!).then(setSelected).catch(() => setSelected([]))
  }, [open, Member])

  const onOk = async () => {
    if (!Member) return
    try {
      await memberApi.saveAuthGroups(Member.memberId!, selected)
      message.success('저장되었습니다.')
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  return (
    <Modal open={open} title={`권한그룹 지정 — ${Member?.memberName ?? ''}`} onOk={onOk} onCancel={onClose} okText="저장" cancelText="취소">
      <Select
        mode="multiple"
        style={{ width: '100%' }}
        placeholder="권한그룹 선택"
        options={options}
        value={selected}
        onChange={setSelected}
      />
    </Modal>
  )
}
