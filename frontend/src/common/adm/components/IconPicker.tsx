import { useMemo, useState } from 'react'
import { Button, Empty, Input, Modal, Space } from 'antd'
import MenuGlyph, { MENU_ICON_ALIASES, MENU_ICON_KEYS } from './MenuGlyph'

/**
 * 아이콘 선택기 — 버튼을 누르면 모달이 열리고, 검색해서 클릭으로 고른다.
 *
 * <p><b>왜 드롭다운이 아니라 모달인가.</b> 아이콘이 수십 개가 되면 select 목록은 한 줄씩 스크롤해야
 * 해서 원하는 그림을 찾기 어렵다. 그리드로 펼쳐 놓으면 눈으로 훑어 찾을 수 있다.
 *
 * <p>검색은 키(영문)와 <b>한글 별칭</b>을 함께 본다 — 운영자가 '방패'를 찾는데 'shield'를 쳐야 하면
 * 검색이 없는 것과 같다(별칭은 MenuGlyph의 {@link MENU_ICON_ALIASES}).
 *
 * <p>폼에서 쓸 때는 AntD Form.Item이 value/onChange를 주입한다(별도 배선 불필요).
 */
export default function IconPicker({
  value,
  onChange,
  disabled,
}: {
  value?: string
  onChange?: (v?: string) => void
  disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')

  const keys = useMemo(() => {
    const kw = keyword.trim().toLowerCase()
    if (!kw) return MENU_ICON_KEYS
    return MENU_ICON_KEYS.filter(
      (k) => k.includes(kw) || (MENU_ICON_ALIASES[k] ?? '').toLowerCase().includes(kw),
    )
  }, [keyword])

  const pick = (k?: string) => {
    onChange?.(k)
    setOpen(false)
  }

  return (
    <>
      <Space>
        <Button onClick={() => { setKeyword(''); setOpen(true) }} disabled={disabled}>
          <Space size={6}>
            {value ? <MenuGlyph name={value} size={16} /> : null}
            <span>{value ?? '아이콘 선택'}</span>
          </Space>
        </Button>
        {value && (
          <Button type="text" onClick={() => onChange?.(undefined)} disabled={disabled}>
            지우기
          </Button>
        )}
      </Space>

      <Modal
        open={open}
        title="아이콘 선택"
        onCancel={() => setOpen(false)}
        footer={null}
        width={640}
      >
        <Input.Search
          placeholder="이름 또는 한글로 검색 (예: 방패, 게시판, user)"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ marginBottom: 12 }}
        />
        {keys.length === 0 ? (
          <Empty description="검색 결과가 없습니다." image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(84px, 1fr))',
              gap: 8,
              maxHeight: 420,
              overflowY: 'auto',
            }}
          >
            {keys.map((k) => {
              const on = k === value
              return (
                <button
                  key={k}
                  type="button"
                  onClick={() => pick(k)}
                  title={`${k}${MENU_ICON_ALIASES[k] ? ` — ${MENU_ICON_ALIASES[k]}` : ''}`}
                  style={{
                    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
                    padding: '10px 4px', cursor: 'pointer', background: on ? '#e6f4ff' : '#fff',
                    border: `1px solid ${on ? '#1677ff' : '#f0f0f0'}`, borderRadius: 8,
                  }}
                >
                  <MenuGlyph name={k} size={22} />
                  {/* 키를 함께 보여준다 — 저장되는 값이 무엇인지 알아야 data.sql·문서와 맞출 수 있다 */}
                  <span style={{ fontSize: 11, color: '#888', wordBreak: 'break-all', lineHeight: 1.2 }}>{k}</span>
                </button>
              )
            })}
          </div>
        )}
      </Modal>
    </>
  )
}
