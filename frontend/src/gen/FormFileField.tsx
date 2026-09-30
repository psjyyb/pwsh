import { useState } from 'react'
import { Button, Space, Upload, message } from 'antd'
import type { UploadProps } from 'antd'
import { fileApi } from '../api/file'
import type { FileMeta } from '../api/file'

interface Props {
  /** 파일 ID 목록(줄바꿈 구분) — 서버의 form_answer_value.value와 같은 형태 */
  value?: string
  onChange?: (v: string) => void
}

/**
 * 폼 파일첨부 문항 입력.
 *
 * <p>고르는 즉시 업로드하고 값에는 <b>파일 ID만</b> 담는다. 제출할 때 서버가 그 ID를 보고
 * 응답에 매핑한다 — 그때 "내가 올린 파일인지"를 다시 확인하므로, 여기서 넘기는 ID를 조작해도
 * 남의 파일을 붙일 수 없다.
 *
 * <p>⚠ 업로드는 인증이 필요하다. 그래서 파일첨부 문항이 있는 폼은 저장 시점에
 * '로그인 필요'로 강제된다(FormService.assertFileFieldAllowed).
 *
 * <p>여기서 뺀 파일은 서버에서 지우지 않는다 — 제출 전이라 아직 아무 데도 안 붙은 상태이고,
 * 매핑 없는 업로드는 유예시간 뒤 고아 파일 GC가 회수한다.
 */
export default function FormFileField({ value, onChange }: Props) {
  const [metas, setMetas] = useState<FileMeta[]>([])

  const ids = value ? value.split('\n').filter(Boolean) : []

  const uploadProps: UploadProps = {
    showUploadList: false,
    multiple: true,
    customRequest: async (opt) => {
      try {
        const uploaded = await fileApi.upload([opt.file as File])
        const meta = uploaded[0]
        if (meta?.fileId) {
          setMetas((prev) => [...prev, meta])
          onChange?.([...ids, meta.fileId].join('\n'))
        }
        opt.onSuccess?.({})
      } catch (e) {
        opt.onError?.(e as Error)
        message.error(e instanceof Error ? e.message : '업로드에 실패했습니다.')
      }
    },
  }

  const removeAt = (fileId: string) => {
    setMetas((prev) => prev.filter((m) => m.fileId !== fileId))
    onChange?.(ids.filter((id) => id !== fileId).join('\n'))
  }

  return (
    <div>
      <Upload {...uploadProps}>
        <Button>파일 선택</Button>
      </Upload>
      {metas.length > 0 && (
        <Space direction="vertical" size={4} style={{ marginTop: 8, width: '100%' }}>
          {metas.map((m) => (
            <Space key={m.fileId} size={8}>
              <span style={{ fontSize: 13 }}>{m.originalName}</span>
              <Button size="small" type="link" danger onClick={() => removeAt(m.fileId!)}>
                빼기
              </Button>
            </Space>
          ))}
        </Space>
      )}
    </div>
  )
}
