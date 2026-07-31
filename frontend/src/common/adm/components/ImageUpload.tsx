import { useEffect, useState } from 'react'
import { Button, Space, Upload, message } from 'antd'
import type { UploadProps } from 'antd'
import { fileApi } from '../../../api/file'

interface Props {
  /** 이미지 file_id (Form.Item 연동) */
  value?: string
  onChange?: (fileId: string) => void
}

/**
 * 단일 이미지 업로드 — 선택 즉시 업로드해 file_id를 onChange로 전달, 썸네일 미리보기.
 * 엔티티(예: 팝업)의 file_id 컬럼에 그대로 저장.
 *   <Form.Item name="fileId"><ImageUpload /></Form.Item>
 */
export default function ImageUpload({ value, onChange }: Props) {
  const [preview, setPreview] = useState('')

  // 기존 값(file_id) 있으면 서버에서 미리보기 로드
  useEffect(() => {
    let url = ''
    let alive = true
    if (value) {
      fileApi
        .imageUrl(value)
        .then((u) => {
          if (alive) { url = u; setPreview(u) } else URL.revokeObjectURL(u)
        })
        .catch(() => setPreview(''))
    } else {
      setPreview('')
    }
    return () => {
      alive = false
      if (url) URL.revokeObjectURL(url)
    }
  }, [value])

  const uploadProps: UploadProps = {
    showUploadList: false,
    accept: 'image/*',
    customRequest: async (opt) => {
      try {
        const metas = await fileApi.upload([opt.file as File])
        const fid = metas[0]?.fileId
        if (fid) onChange?.(fid) // 미리보기는 value 변경 → useEffect가 로드
        opt.onSuccess?.({})
      } catch (e) {
        opt.onError?.(e as Error)
        message.error('이미지 업로드에 실패했습니다.')
      }
    },
  }

  return (
    <div>
      {preview && (
        <img
          src={preview}
          alt="팝업 이미지"
          style={{ display: 'block', maxWidth: 240, maxHeight: 160, marginBottom: 8, border: '1px solid #eee', borderRadius: 4 }}
        />
      )}
      <Space>
        <Upload {...uploadProps}>
          <Button>{value ? '이미지 변경' : '이미지 선택'}</Button>
        </Upload>
        {value && <Button danger onClick={() => onChange?.('')}>제거</Button>}
      </Space>
    </div>
  )
}
