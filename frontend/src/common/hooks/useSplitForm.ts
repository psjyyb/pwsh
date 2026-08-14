import { useState } from 'react'
import { Form, message } from 'antd'

interface CrudApiLike<T> {
  view: (rowId: string) => Promise<T>
  insert: (vo: Partial<T>) => Promise<unknown>
  update: (vo: Partial<T>) => Promise<unknown>
  remove: (rowId: string) => Promise<unknown>
}

export type SplitMode = 'none' | 'insert' | 'edit'

/**
 * 분할 마스터-디테일(목록 | 상세·등록·수정) 공통 로직.
 * 폼 컴포넌트 1개를 재사용: mode로 등록/수정 구분, 수정 시 rowId(자기 PK)로 갱신.
 *   const { form, mode, selectedKey, openNew, openRow, save, remove } = useSplitForm(codeApi, reload)
 * - openRow(rowId): 해당 행 상세를 폼에 바인딩(수정 모드) — view.do로 전체 필드 로드
 * - openNew(): 빈 폼(등록 모드)
 * - save(): mode에 따라 insert/update, 저장 후 onChanged(목록 갱신)
 * - remove(): 선택 행 삭제 후 폼 초기화 + onChanged
 */
export function useSplitForm<T extends Record<string, any>>(api: CrudApiLike<T>, onChanged: () => void) {
  const [form] = Form.useForm()
  const [mode, setMode] = useState<SplitMode>('none')
  const [selectedKey, setSelectedKey] = useState<string | null>(null)

  const openNew = () => {
    form.resetFields()
    setSelectedKey(null)
    setMode('insert')
  }

  /** 선택 해제 + 폼 비활성(none) — 목록 필터 전환 등에서 사용 */
  const reset = () => {
    form.resetFields()
    setSelectedKey(null)
    setMode('none')
  }

  const openRow = async (rowId: string) => {
    try {
      const data = await api.view(rowId)
      form.resetFields()
      form.setFieldsValue(data)
      setSelectedKey(rowId)
      setMode('edit')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회에 실패했습니다.')
    }
  }

  const save = async () => {
    const values = await form.validateFields()
    try {
      if (mode === 'edit') {
        await api.update({ ...values, rowId: selectedKey })
      } else {
        await api.insert(values)
      }
      message.success('저장되었습니다.')
      onChanged()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  const remove = async () => {
    if (!selectedKey) return
    try {
      await api.remove(selectedKey)
      message.success('삭제되었습니다.')
      form.resetFields()
      setSelectedKey(null)
      setMode('none')
      onChanged()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제에 실패했습니다.')
    }
  }

  return { form, mode, selectedKey, openNew, openRow, save, remove, reset }
}
