import { useEffect, useState } from 'react'
import { Modal, Transfer, message } from 'antd'
import type { TransferProps } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { MEMBER_LIST_URL } from '../member/member.api'
import type { Member } from '../member/member.api'
import { authGroupApi } from './authgroup.api'
import type { AuthGroup } from './authgroup.api'

interface Props {
  open: boolean
  authgroup: AuthGroup | null
  onClose: () => void
}

interface MemberItem {
  key: string
  title: string
}

/** 권한그룹 소속 사용자 지정 — Transfer(좌: 미지정 / 우: 지정, 각 패널 검색)로 다수 사용자도 관리 용이 */
export default function AuthGroupMemberModal({ open, authgroup, onClose }: Props) {
  const [items, setItems] = useState<MemberItem[]>([])
  const [targetKeys, setTargetKeys] = useState<string[]>([])

  useEffect(() => {
    if (!open || !authgroup) return
    apiPost<ListResult<Member>>(MEMBER_LIST_URL, { pageNo: 1, pageSize: 1000 })
      .then((r) => setItems(r.list.map((u) => ({ key: u.memberId!, title: `${u.memberName} (${u.memberId})` }))))
      .catch(() => setItems([]))
    authGroupApi.getMemberIds(authgroup.rowId!).then(setTargetKeys).catch(() => setTargetKeys([]))
  }, [open, authgroup])

  const onChange: TransferProps<MemberItem>['onChange'] = (next) => setTargetKeys(next as string[])

  const onOk = async () => {
    if (!authgroup) return
    try {
      await authGroupApi.saveMembers(authgroup.rowId!, targetKeys)
      message.success('저장되었습니다.')
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  return (
    <Modal
      open={open}
      title={`사용자 지정 — ${authgroup?.authGroupName ?? ''}`}
      onOk={onOk}
      onCancel={onClose}
      okText="저장"
      cancelText="취소"
      width={640}
    >
      <Transfer<MemberItem>
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
