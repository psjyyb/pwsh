import { useRef, useState } from 'react'
import { Button, Input, Space, Upload, message } from 'antd'
import type { UploadProps } from 'antd'
import { fileApi } from '../../../api/file'

/** 갤러리 사진 항목 (fileId + 캡션). 순서 = 표시 순서(첫 항목이 대표) */
export interface GalleryItem {
  fileId: string
  fileOrgNm?: string
  fileDesc?: string
}

interface Props {
  onChange?: (items: GalleryItem[]) => void
  /** 편집 진입 시 기존 사진(캡션 포함). 상위에서 key로 재마운트 */
  initialItems?: GalleryItem[]
  /** 사진당 최대 용량(MB) */
  maxSizeMb?: number
}

/**
 * 갤러리 전용 다중 이미지 업로더 — 사진마다 설명(캡션) 입력 + 순서변경(▲▼) + 삭제.
 * 저장은 상위에서 fileApi.saveMapping(mapKey, 'BBS_IMG', fileIds, fileDescs)로 연결.
 */
export default function GalleryImages({ onChange, initialItems = [], maxSizeMb }: Props) {
  const [items, setItems] = useState<GalleryItem[]>(initialItems)
  const itemsRef = useRef<GalleryItem[]>(initialItems) // 동시 업로드 stale 방지

  const sync = (next: GalleryItem[]) => {
    itemsRef.current = next
    setItems(next)
    onChange?.(next)
  }

  const uploadProps: UploadProps = {
    multiple: true,
    showUploadList: false,
    accept: 'image/*',
    beforeUpload: (file) => {
      if (maxSizeMb && file.size > maxSizeMb * 1024 * 1024) {
        message.warning(`사진당 최대 ${maxSizeMb}MB까지 가능합니다. (${(file.size / 1024 / 1024).toFixed(1)}MB)`)
        return Upload.LIST_IGNORE
      }
      return true
    },
    customRequest: async (opt) => {
      try {
        const metas = await fileApi.upload([opt.file as File])
        const m = metas[0]
        if (m?.fileId) sync([...itemsRef.current, { fileId: m.fileId, fileOrgNm: m.fileOrgNm, fileDesc: '' }])
        opt.onSuccess?.({})
      } catch (e) {
        opt.onError?.(e as Error)
        message.error('이미지 업로드에 실패했습니다.')
      }
    },
  }

  const setDesc = (fileId: string, desc: string) =>
    sync(itemsRef.current.map((it) => (it.fileId === fileId ? { ...it, fileDesc: desc } : it)))
  const remove = (fileId: string) => sync(itemsRef.current.filter((it) => it.fileId !== fileId))
  const move = (idx: number, dir: -1 | 1) => {
    const next = [...itemsRef.current]
    const j = idx + dir
    if (j < 0 || j >= next.length) return
    ;[next[idx], next[j]] = [next[j], next[idx]]
    sync(next)
  }

  return (
    <div>
      <Space>
        <Upload {...uploadProps}>
          <Button>사진 추가</Button>
        </Upload>
        <span style={{ color: '#999', fontSize: 12 }}>첫 번째 사진이 목록 대표 이미지가 됩니다</span>
      </Space>
      <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {items.map((it, idx) => (
          <div
            key={it.fileId}
            style={{ display: 'flex', gap: 8, alignItems: 'center', border: '1px solid #f0f0f0', borderRadius: 6, padding: 8 }}
          >
            <img
              src={`/api/pub/image/${it.fileId}`}
              alt=""
              style={{ width: 96, height: 64, objectFit: 'cover', borderRadius: 4, border: '1px solid #eee', flex: '0 0 auto' }}
            />
            <Input
              value={it.fileDesc}
              onChange={(e) => setDesc(it.fileId, e.target.value)}
              placeholder="사진 설명(캡션)"
              style={{ flex: 1 }}
            />
            <Space>
              <Button size="small" onClick={() => move(idx, -1)} disabled={idx === 0}>▲</Button>
              <Button size="small" onClick={() => move(idx, 1)} disabled={idx === items.length - 1}>▼</Button>
              <a onClick={() => remove(it.fileId)}>삭제</a>
            </Space>
          </div>
        ))}
      </div>
    </div>
  )
}
