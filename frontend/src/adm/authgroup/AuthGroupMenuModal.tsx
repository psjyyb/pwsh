import { useEffect, useState } from 'react'
import { Modal, Tree, message } from 'antd'
import type { TreeDataNode } from 'antd'
import { menuApi } from '../menu/menu.api'
import type { Menu } from '../menu/menu.api'
import { authGroupApi } from './authgroup.api'
import type { AuthGroup } from './authgroup.api'

/** 플랫 메뉴 → AntD Tree 데이터 */
function toTreeData(list: Menu[]): TreeDataNode[] {
  const byParent = new Map<string, Menu[]>()
  for (const m of list) {
    const p = m.pMenuId ?? '0'
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p)!.push(m)
  }
  const build = (pid: string): TreeDataNode[] =>
    (byParent.get(pid) ?? []).map((m) => ({ key: m.rowId!, title: m.menuName, children: build(m.rowId!) }))
  return build('0')
}

interface Props {
  open: boolean
  authgroup: AuthGroup | null
  onClose: () => void
}

/** 권한그룹의 접근 가능 메뉴를 트리 체크로 설정 (checkStrictly=상/하위 독립 선택) */
export default function AuthGroupMenuModal({ open, authgroup, onClose }: Props) {
  const [treeData, setTreeData] = useState<TreeDataNode[]>([])
  const [checked, setChecked] = useState<string[]>([])
  const [expandedKeys, setExpandedKeys] = useState<string[]>([])

  useEffect(() => {
    if (!open || !authgroup) return
    // 관리자(ADM)·사용자(GEN) 메뉴를 모두 로드해 한 트리에 표시(권한필터 없는 관리트리).
    // → 사용자 메뉴(취미게시판/모집/페이지 등)에도 그룹 권한을 부여할 수 있어야 함.
    Promise.all([menuApi.manageTree('ADM'), menuApi.manageTree('GEN')])
      .then(([adm, gen]) => {
        setTreeData([
          { key: 'grp-ADM', title: '관리자 메뉴 (ADM)', checkable: false, selectable: false, children: toTreeData(adm) },
          { key: 'grp-GEN', title: '사용자 메뉴 (GEN)', checkable: false, selectable: false, children: toTreeData(gen) },
        ])
        setExpandedKeys(['grp-ADM', 'grp-GEN', ...adm.map((m) => m.rowId!), ...gen.map((m) => m.rowId!)])
      })
      .catch(() => setTreeData([]))
    authGroupApi.getMenuIds(authgroup.rowId!).then(setChecked).catch(() => setChecked([]))
  }, [open, authgroup])

  const onOk = async () => {
    if (!authgroup) return
    try {
      await authGroupApi.saveMenu(authgroup.rowId!, checked)
      message.success('저장되었습니다.')
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  return (
    <Modal open={open} title={`권한 설정 — ${authgroup?.authGroupName ?? ''}`} onOk={onOk} onCancel={onClose} okText="저장" cancelText="취소">
      <Tree
        checkable
        checkStrictly
        treeData={treeData}
        checkedKeys={checked}
        expandedKeys={expandedKeys}
        onExpand={(keys) => setExpandedKeys(keys.map(String))}
        onCheck={(keys) => setChecked((Array.isArray(keys) ? keys : keys.checked).map(String))}
      />
    </Modal>
  )
}
