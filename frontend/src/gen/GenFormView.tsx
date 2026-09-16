import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Alert, Checkbox, Input, Radio, Result, Select, Space, Spin, message } from 'antd'
import { PageBody, PageHead } from '../common/gen/components/PageShell'
import { CHOICE_FIELDS, formApi } from '../adm/form/form.api'
import type { Form, FormField } from '../adm/form/form.api'
import { formAnswerApi } from '../adm/formanswer/formanswer.api'

/** 선택지 문자열 → 목록 */
const optionsOf = (f: FormField) =>
  (f.options ?? '').split('\n').map((s) => s.trim()).filter(Boolean)

/**
 * 사용자 폼 화면 — 메뉴(연결유형 '폼')의 conn_id(form_id)로 폼+문항을 읽어 그대로 그린다.
 *
 * <p>문항이 DB에 있으므로 새 신청서·설문을 만들 때 이 파일을 고칠 일이 없다. 화면은
 * 유형별 입력 위젯만 안다. 검증(필수·선택지·기간·중복)은 <b>서버가 다시 한다</b> —
 * 여기 검증은 사용자의 실수를 줄여줄 뿐이다.
 */
export default function GenFormView() {
  const { formId } = useParams()
  const [form, setForm] = useState<Form | null>(null)
  const [values, setValues] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!formId) return
    setLoading(true)
    setDone(false)
    setValues({})
    formApi
      .view(formId)
      .then((f) => { setForm(f); setError('') })
      .catch((e) => setError(e instanceof Error ? e.message : '폼을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [formId])

  const setValue = (fieldId: string, v: string) => setValues((prev) => ({ ...prev, [fieldId]: v }))

  const submit = async () => {
    const missing = (form?.fields ?? []).find((f) => f.requiredYn === 'Y' && !values[f.rowId!]?.trim())
    if (missing) {
      message.warning(`${missing.label}은(는) 필수입니다.`)
      return
    }
    setBusy(true)
    try {
      await formAnswerApi.submit(formId!, values)
      setDone(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '제출에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const widget = (f: FormField) => {
    const v = values[f.rowId!] ?? ''
    const set = (x: string) => setValue(f.rowId!, x)
    switch (f.fieldCd) {
      case 'FIELD02':
        return <Input.TextArea rows={5} placeholder={f.placeholder} value={v} onChange={(e) => set(e.target.value)} />
      case 'FIELD03':
        return (
          <Radio.Group value={v} onChange={(e) => set(e.target.value)}>
            <Space direction="vertical">
              {optionsOf(f).map((o) => <Radio key={o} value={o}>{o}</Radio>)}
            </Space>
          </Radio.Group>
        )
      case 'FIELD04':
        return (
          <Checkbox.Group
            value={v ? v.split('\n') : []}
            onChange={(list) => set((list as string[]).join('\n'))}
          >
            <Space direction="vertical">
              {optionsOf(f).map((o) => <Checkbox key={o} value={o}>{o}</Checkbox>)}
            </Space>
          </Checkbox.Group>
        )
      case 'FIELD05':
        return (
          <Select
            style={{ width: 260 }}
            placeholder={f.placeholder ?? '선택하세요'}
            value={v || undefined}
            options={optionsOf(f).map((o) => ({ value: o, label: o }))}
            onChange={set}
          />
        )
      // 날짜는 브라우저 기본 위젯을 쓴다 — gen 영역은 AntD 스타일을 입히지 않고, 값도 YYYY-MM-DD로 바로 나온다
      case 'FIELD06':
        return <Input type="date" style={{ width: 200 }} value={v} onChange={(e) => set(e.target.value)} />
      case 'FIELD07':
        return <Input type="number" style={{ width: 200 }} placeholder={f.placeholder} value={v} onChange={(e) => set(e.target.value)} />
      case 'FIELD08':
        return <Input type="email" placeholder={f.placeholder ?? 'name@example.com'} value={v} onChange={(e) => set(e.target.value)} />
      default:
        return <Input placeholder={f.placeholder} value={v} onChange={(e) => set(e.target.value)} />
    }
  }

  if (loading) {
    return <PageBody narrow><div style={{ padding: 60, textAlign: 'center' }}><Spin /></div></PageBody>
  }
  if (error || !form) {
    return (
      <>
        <PageHead eyebrow="신청" title="폼" />
        <PageBody narrow>
          <Result status="warning" title="열 수 없는 폼입니다" subTitle={error} />
        </PageBody>
      </>
    )
  }

  return (
    <>
      <PageHead eyebrow={form.typeName ?? '신청'} title={form.title ?? ''} lead={form.description ?? undefined} />
      <PageBody narrow>
        {done ? (
          <Result
            status="success"
            title="제출되었습니다"
            subTitle={form.doneMessage || '접수해 주셔서 감사합니다.'}
          />
        ) : (
          <>
            {form.endDt && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={`접수 마감: ${form.endDt}`} />}
            {form.loginYn !== 'N' && (
              <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="로그인 후 제출할 수 있습니다." />
            )}
            <Space direction="vertical" size={22} style={{ width: '100%' }}>
              {(form.fields ?? []).map((f) => (
                <div key={f.rowId}>
                  <div style={{ marginBottom: 8, fontWeight: 600 }}>
                    {f.label}
                    {f.requiredYn === 'Y' && <span style={{ color: '#d4380d', marginLeft: 4 }}>*</span>}
                    {f.privacyYn === 'Y' && (
                      <span style={{ marginLeft: 8, fontSize: 12.5, color: '#888', fontWeight: 400 }}>
                        개인정보 — 암호화해 보관합니다
                      </span>
                    )}
                  </div>
                  {CHOICE_FIELDS.includes(f.fieldCd ?? '') && optionsOf(f).length === 0 ? (
                    <span style={{ color: '#999' }}>선택지가 설정되지 않았습니다.</span>
                  ) : (
                    widget(f)
                  )}
                </div>
              ))}
            </Space>
            <div style={{ marginTop: 28 }}>
              {/* AntD Button을 쓰지 않는다 — gen 영역은 gen.css 토큰으로만 그린다 */}
              <button type="button" className="gen-btn gen-btn-primary" onClick={submit} disabled={busy}>
                {busy ? '제출 중…' : '제출하기'}
              </button>
            </div>
          </>
        )}
      </PageBody>
    </>
  )
}
