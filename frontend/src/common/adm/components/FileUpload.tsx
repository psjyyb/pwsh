import { useRef, useState } from 'react'
import { Button, List, Space, Upload, message } from 'antd'
import type { UploadProps } from 'antd'
import { fileApi } from '../../../api/file'
import type { FileMeta } from '../../../api/file'

interface Props {
  onChange?: (fileIds: string[]) => void
  /** 편집 진입 시 기존 첨부 메타(파일명/다운로드/삭제 표시용). 글 전환 시 상위에서 key로 재마운트. */
  initialFiles?: FileMeta[]
  /** 최대 첨부 개수(초과 업로드 거부). 미지정 시 무제한. */
  maxCount?: number
  /** 파일당 최대 용량(MB, 초과 업로드 거부). 미지정 시 서버 전역 제한만 적용. */
  maxSizeMb?: number
}

/** KB/MB 표기 */
function fmtSize(size?: string): string {
  const n = Number(size)
  if (!n || Number.isNaN(n)) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 재사용 파일 첨부 — 기존 첨부 목록 표시 + 다운로드/삭제, 신규 업로드 추가.
 * 업로드 즉시 서버 저장(file_id 확보), 목록 변경 시 현재 file_id 배열을 onChange로 전달.
 * 엔티티 저장 시 상위에서 fileApi.saveMapping(mapKey, loc, fileIds)로 최종 연결(삭제분은 매핑에서 제외됨).
 */
export default function FileUpload({ onChange, initialFiles = [], maxCount, maxSizeMb }: Props) {
  const [items, setItems] = useState<FileMeta[]>(initialFiles)
  // 동시(multiple) 업로드 시 stale closure 방지를 위해 ref로 현재 목록 추적
  const itemsRef = useRef<FileMeta[]>(initialFiles)

  const sync = (next: FileMeta[]) => {
    itemsRef.current = next
    setItems(next)
    onChange?.(next.map((m) => m.fileId!))
  }

  const oversize = (f: { size: number }) => !!maxSizeMb && f.size > maxSizeMb * 1024 * 1024

  const uploadProps: UploadProps = {
    multiple: true,
    showUploadList: false,
    // 검증은 여기서(배치 전체 기준). customRequest는 업로드만 담당.
    beforeUpload: (file, fileList) => {
      // 1) 용량 초과 파일 거부
      if (oversize(file)) {
        message.warning(`파일당 최대 ${maxSizeMb}MB까지 업로드할 수 있습니다. (${(file.size / 1024 / 1024).toFixed(1)}MB)`)
        return Upload.LIST_IGNORE
      }
      // 2) 개수 제한: 확정본 + 이번 배치에서 용량 통과한 앞선 파일 수로 판정(동시/다중 선택 초과 방지)
      if (maxCount) {
        const priorInBatch = fileList.filter((f) => !oversize(f)).indexOf(file)
        const projected = itemsRef.current.length + priorInBatch
        if (projected >= maxCount) {
          if (projected === maxCount) message.warning(`첨부는 최대 ${maxCount}개까지 가능합니다.`)
          return Upload.LIST_IGNORE
        }
      }
      return true
    },
    customRequest: async (opt) => {
      try {
        const metas = await fileApi.upload([opt.file as File])
        sync([...itemsRef.current, ...metas])
        opt.onSuccess?.({})
      } catch (e) {
        opt.onError?.(e as Error)
        message.error('업로드에 실패했습니다.')
      }
    },
  }

  return (
    <div>
      <Space>
        <Upload {...uploadProps}>
          <Button>파일 선택</Button>
        </Upload>
        {maxCount || maxSizeMb ? (
          <span style={{ color: '#999', fontSize: 12 }}>
            {[maxCount && `최대 ${maxCount}개`, maxSizeMb && `파일당 ${maxSizeMb}MB`].filter(Boolean).join(' · ')}
          </span>
        ) : null}
      </Space>
      {items.length > 0 && (
        <List
          size="small"
          style={{ marginTop: 8 }}
          dataSource={items}
          renderItem={(m) => (
            <List.Item
              actions={[
                <a key="dl" onClick={() => fileApi.download(m.fileId!, m.fileOrgNm ?? 'file')}>다운로드</a>,
                <a key="del" onClick={() => sync(itemsRef.current.filter((x) => x.fileId !== m.fileId))}>삭제</a>,
              ]}
            >
              <span>
                {m.fileOrgNm}
                {fmtSize(m.fileSize) && <span style={{ color: '#999', marginLeft: 8 }}>({fmtSize(m.fileSize)})</span>}
              </span>
            </List.Item>
          )}
        />
      )}
    </div>
  )
}
