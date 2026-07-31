import { useEffect, useState } from 'react'
import { Modal, Select, message } from 'antd'
import { authgrpApi } from '../authgrp/authgrp.api'
import { userApi } from './user.api'
import type { User } from './user.api'

interface Props {
  open: boolean
  user: User | null
  onClose: () => void
}

/** 사용자에게 권한그룹 지정 (다중 선택) */
export default function AuthgrpAssignModal({ open, user, onClose }: Props) {
  const [options, setOptions] = useState<{ value: string; label: string }[]>([])
  const [selected, setSelected] = useState<string[]>([])

  useEffect(() => {
    if (!open || !user) return
    authgrpApi
      .combo()
      .then((list) => setOptions(list.map((g) => ({ value: g.authgrpId!, label: `${g.authgrpNm} (${g.authgrpId})` }))))
      .catch(() => setOptions([]))
    userApi.getAuthgrps(user.userId!).then(setSelected).catch(() => setSelected([]))
  }, [open, user])

  const onOk = async () => {
    if (!user) return
    try {
      await userApi.saveAuthgrps(user.userId!, selected)
      message.success('저장되었습니다.')
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  return (
    <Modal open={open} title={`권한그룹 지정 — ${user?.userNm ?? ''}`} onOk={onOk} onCancel={onClose} okText="저장" cancelText="취소">
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
