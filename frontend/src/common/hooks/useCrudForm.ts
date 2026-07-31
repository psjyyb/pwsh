import { useEffect } from 'react'
import { Form, message } from 'antd'

interface Options<T> {
  open: boolean
  editing: T | null // null=등록, 값=수정
  insert: (vo: any) => Promise<unknown>
  update: (vo: any) => Promise<unknown>
  onSaved: () => void
  onClose: () => void
}

/**
 * 등록/수정 모달 공통 로직 — 폼 인스턴스 + open 시 값 세팅/초기화 + 저장(insert/update) 처리.
 * 수정 시 PK는 dbKey(해당 테이블 자기 PK)로 넘긴다. editing(목록 행)에는 dbKey가 담겨 있다.
 *   const { form, submit } = useCrudForm({ open, editing,
 *     insert: codeApi.insert, update: codeApi.update, onSaved, onClose })
 */
export function useCrudForm<T extends Record<string, any>>(opts: Options<T>) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (!opts.open) return
    if (opts.editing) {
      form.setFieldsValue(opts.editing)
    } else {
      form.resetFields()
    }
  }, [opts.open, opts.editing, form])

  const submit = async () => {
    const values = await form.validateFields()
    try {
      if (opts.editing) {
        await opts.update({ ...values, dbKey: opts.editing.dbKey })
        message.success('수정되었습니다.')
      } else {
        await opts.insert(values)
        message.success('등록되었습니다.')
      }
      opts.onSaved()
      opts.onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  return { form, submit }
}
