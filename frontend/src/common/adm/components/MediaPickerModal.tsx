import { useEffect, useState } from 'react'
import { Empty, Input, Modal, Pagination, Spin, message } from 'antd'
import { apiPost } from '../../../api/http'
import type { ListResult } from '../../../api/http'
import { FILE_LIST_URL, isImage } from '../../../adm/file/file.api'
import type { MediaFile } from '../../../adm/file/file.api'
import AuthImage from './AuthImage'

interface Props {
  open: boolean
  onClose: () => void
  /** 고른 파일의 file_id */
  onPick: (fileId: string) => void
}

const PAGE_SIZE = 12

/**
 * 미디어 라이브러리에서 이미지 고르기.
 *
 * <p>라이브러리에 담긴 이미지만 보여준다 — 게시글 첨부로 올라온 남의 파일까지 섞이면
 * 목록이 금세 쓸 수 없게 되고, 그런 파일은 원래 글에서 빠지는 순간 정리 대상이 된다.
 * 여기서 고른 파일은 저장 시 해당 엔티티 매핑이 추가될 뿐 LIBRARY 매핑은 그대로라,
 * 나중에 그 엔티티를 지워도 원본은 라이브러리에 남는다.
 */
export default function MediaPickerModal({ open, onClose, onPick }: Props) {
  const [rows, setRows] = useState<MediaFile[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async (p: number, kw: string) => {
    setLoading(true)
    try {
      const res = await apiPost<ListResult<MediaFile>>(FILE_LIST_URL, {
        pageNo: p,
        pageSize: PAGE_SIZE,
        filterLibrary: 'Y',
        filterExtGroup: 'IMAGE',
        filterKeyword: kw,
      })
      setRows(res.list)
      setTotal(res.totalCount)
      setPage(p)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  // 열 때마다 1페이지부터 다시 — 닫혀 있는 동안 라이브러리가 바뀌었을 수 있다
  useEffect(() => {
    if (open) {
      setKeyword('')
      load(1, '')
    }
  }, [open])

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={720}
      title="라이브러리에서 이미지 선택"
      destroyOnHidden
    >
      <Input.Search
        allowClear
        placeholder="파일명"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        onSearch={(v) => load(1, v)}
        style={{ marginBottom: 12 }}
      />
      <Spin spinning={loading}>
        {rows.length === 0 ? (
          <Empty
            description="라이브러리에 담긴 이미지가 없습니다. 미디어 라이브러리에서 먼저 올려주세요."
            style={{ padding: '32px 0' }}
          />
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(132px, 1fr))', gap: 12 }}>
            {rows.map((f) => (
              <div
                key={f.fileId}
                onClick={() => {
                  onPick(f.fileId!)
                  onClose()
                }}
                style={{ padding: 6, border: '1px solid #f0f0f0', borderRadius: 8, cursor: 'pointer' }}
              >
                {isImage(f.ext) && <AuthImage fileId={f.fileId} alt={f.originalName} size={116} />}
                <div
                  style={{ marginTop: 6, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                  title={f.originalName}
                >
                  {f.originalName}
                </div>
              </div>
            ))}
          </div>
        )}
        <Pagination
          style={{ marginTop: 12 }}
          align="end"
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          showSizeChanger={false}
          onChange={(p) => load(p, keyword)}
        />
      </Spin>
    </Modal>
  )
}
