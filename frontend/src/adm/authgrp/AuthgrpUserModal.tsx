import { useEffect, useState } from 'react'
import { Modal, Transfer, message } from 'antd'
import type { TransferProps } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { USER_LIST_URL } from '../user/user.api'
import type { User } from '../user/user.api'
import { authgrpApi } from './authgrp.api'
import type { Authgrp } from './authgrp.api'

interface Props {
  open: boolean
  authgrp: Authgrp | null
  onClose: () => void
}

interface UserItem {
  key: string
  title: string
}

/** 권한그룹 소속 사용자 지정 — Transfer(좌: 미지정 / 우: 지정, 각 패널 검색)로 다수 사용자도 관리 용이 */
export default function AuthgrpUserModal({ open, authgrp, onClose }: Props) {
  const [items, setItems] = useState<UserItem[]>([])
  const [targetKeys, setTargetKeys] = useState<string[]>([])

  useEffect(() => {
    if (!open || !authgrp) return
    apiPost<ListResult<User>>(USER_LIST_URL, { pageIndex: 1, size: 1000 })
      .then((r) => setItems(r.list.map((u) => ({ key: u.userId!, title: `${u.userNm} (${u.userId})` }))))
      .catch(() => setItems([]))
    authgrpApi.getUserIds(authgrp.dbKey!).then(setTargetKeys).catch(() => setTargetKeys([]))
  }, [open, authgrp])

  const onChange: TransferProps<UserItem>['onChange'] = (next) => setTargetKeys(next as string[])

  const onOk = async () => {
    if (!authgrp) return
    try {
      await authgrpApi.saveUsers(authgrp.dbKey!, targetKeys)
      message.success('저장되었습니다.')
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  return (
    <Modal
      open={open}
      title={`사용자 지정 — ${authgrp?.authgrpNm ?? ''}`}
      onOk={onOk}
      onCancel={onClose}
      okText="저장"
      cancelText="취소"
      width={640}
    >
      <Transfer<UserItem>
        dataSource={items}
        targetKeys={targetKeys}
        onChange={onChange}
        render={(item) => item.title}
        titles={['미지정', '지정됨']}
        showSearch
        filterOption={(input, item) => item.title.toLowerCase().includes(input.toLowerCase())}
        listStyle={{ width: 280, height: 360 }}
      />
    </Modal>
  )
}
