import { useEffect, useState } from 'react'
import {
  Button, Card, Descriptions, Empty, Pagination, Popconfirm, Segmented, Space, Spin, Table, Tag, Upload, message,
} from 'antd'
import type { TableColumnsType, UploadProps } from 'antd'
import { useList } from '../../common/hooks/useList'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import AuthImage from '../../common/adm/components/AuthImage'
import { fileApi } from '../../api/file'
import { FILE_LIST_URL, FILE_TYPE_LABEL, isImage, mediaApi } from './file.api'
import type { MediaFile, MediaRef } from './file.api'

/** 바이트 문자열 → 사람이 읽는 크기. size 컬럼이 VARCHAR라 숫자가 아닐 수 있다 */
function humanSize(size?: string) {
  const n = Number(size)
  if (!size || Number.isNaN(n)) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 사용처 1건을 사람이 읽는 한 줄로. 대상 이름이 없으면 용도+대상 id로 떨어진다.
 * PROFILE은 file_ref가 아니라 member 직접 참조라 mapKey가 없다 — 이름만 쓴다.
 */
function refLabel(r: MediaRef) {
  const type = FILE_TYPE_LABEL[r.fileType ?? ''] ?? r.fileType ?? '알 수 없음'
  if (r.targetName) return `${type} — ${r.targetName}`
  return r.mapKey ? `${type} #${r.mapKey}` : type
}

/**
 * 미디어 라이브러리 — 올라간 파일을 보고, 미리 올려두고, 어디에 쓰이는지 확인하고 정리한다.
 *
 * <p>라이브러리에 담긴 파일(`libraryYn='Y'`)은 어떤 글에도 안 붙어 있어도 고아 GC가 지우지 않는다.
 * 담기지 않은 파일은 업로드 유예시간이 지나면 새벽 배치가 회수한다 — 목록에서 '미사용'으로 보이는
 * 것들이 그 대상이다.
 *
 * <p>'사용 중' 판정에는 <b>회원 프로필 사진도 포함된다</b>. 프로필만 `file_ref`를 안 거치고
 * `member.profile_file_id`로 직접 참조해서, 빼면 프로필 사진이 전부 '미사용'으로 보이고
 * 관리자가 지워 회원 프로필이 깨진다.
 */
export default function FileListPage() {
  const { rows, total, loading, page, pageSize, reload, search, changePage } = useList<MediaFile>(FILE_LIST_URL)
  const [view, setView] = useState<'grid' | 'list'>('grid')
  const [selected, setSelected] = useState<MediaFile | null>(null)
  const [refs, setRefs] = useState<MediaRef[]>([])
  const [refsLoading, setRefsLoading] = useState(false)
  const [busy, setBusy] = useState(false)

  // 선택한 파일의 사용처는 상세를 열 때만 조회한다 — 목록 20건마다 조인을 돌릴 이유가 없다
  useEffect(() => {
    let alive = true
    if (!selected?.fileId) {
      setRefs([])
      return
    }
    setRefsLoading(true)
    mediaApi
      .refs(selected.fileId)
      .then((r) => alive && setRefs(r))
      .catch(() => alive && setRefs([]))
      .finally(() => alive && setRefsLoading(false))
    return () => {
      alive = false
    }
  }, [selected?.fileId])

  /** 목록이 갱신되면 열려 있는 상세도 최신 행으로 맞춘다(담기/빼기 직후 배지가 안 바뀌면 혼란) */
  useEffect(() => {
    if (!selected?.fileId) return
    const fresh = rows.find((r) => r.fileId === selected.fileId)
    if (fresh && fresh.libraryYn !== selected.libraryYn) setSelected(fresh)
  }, [rows]) // eslint-disable-line react-hooks/exhaustive-deps

  const uploadProps: UploadProps = {
    showUploadList: false,
    multiple: true,
    customRequest: async (opt) => {
      try {
        await mediaApi.uploadLibrary([opt.file as File])
        opt.onSuccess?.({})
        message.success('라이브러리에 올렸습니다.')
        reload()
      } catch (e) {
        opt.onError?.(e as Error)
        message.error(e instanceof Error ? e.message : '업로드에 실패했습니다.')
      }
    },
  }

  const toggleLibrary = async (f: MediaFile) => {
    const add = f.libraryYn !== 'Y'
    try {
      await mediaApi.setLibrary(f.fileId!, add)
      message.success(add ? '라이브러리에 담았습니다.' : '라이브러리에서 뺐습니다.')
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '처리에 실패했습니다.')
    }
  }

  const remove = async (f: MediaFile) => {
    try {
      await mediaApi.remove(f.fileId!)
      message.success('삭제했습니다.')
      setSelected(null)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제에 실패했습니다.')
    }
  }

  /** 고아 정리 수동 실행 — 평소엔 새벽 배치가 돈다 */
  const runGc = async () => {
    setBusy(true)
    try {
      const deleted = await mediaApi.gc()
      message.success(`고아 파일 ${deleted}건을 정리했습니다.`)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '정리에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const columns: TableColumnsType<MediaFile> = [
    {
      title: '미리보기',
      width: 72,
      render: (_, r) => (isImage(r.ext) ? <AuthImage fileId={r.fileId} alt={r.originalName} size={48} /> : null),
    },
    { title: '파일명', dataIndex: 'originalName', ellipsis: true },
    { title: '형식', dataIndex: 'ext', width: 70 },
    { title: '크기', width: 90, render: (_, r) => humanSize(r.size) },
    {
      title: '사용',
      width: 90,
      render: (_, r) =>
        Number(r.refCnt) > 0 ? <Tag color="blue">{r.refCnt}곳</Tag> : <Tag>미사용</Tag>,
    },
    { title: '라이브러리', width: 90, render: (_, r) => (r.libraryYn === 'Y' ? <Tag color="green">담김</Tag> : '-') },
    { title: '등록일', dataIndex: 'regDt', width: 160 },
  ]

  const grid = (
    <Spin spinning={loading}>
      {rows.length === 0 ? (
        <Empty description="파일이 없습니다." style={{ padding: '40px 0' }} />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: 12 }}>
          {rows.map((f) => (
            <div
              key={f.fileId}
              onClick={() => setSelected(f)}
              style={{
                padding: 8,
                borderRadius: 8,
                cursor: 'pointer',
                border: f.fileId === selected?.fileId ? '2px solid #1677ff' : '1px solid #f0f0f0',
              }}
            >
              {isImage(f.ext) ? (
                <AuthImage fileId={f.fileId} alt={f.originalName} size={124} />
              ) : (
                <div
                  style={{
                    width: 124, height: 124, display: 'flex', alignItems: 'center', justifyContent: 'center',
                    background: '#fafafa', border: '1px solid #f0f0f0', borderRadius: 6,
                    color: '#888', fontSize: 16, fontWeight: 600,
                  }}
                >
                  {(f.ext ?? 'FILE').toUpperCase()}
                </div>
              )}
              <div style={{ marginTop: 6, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {f.originalName}
              </div>
              <Space size={4} style={{ marginTop: 2 }}>
                {f.libraryYn === 'Y' && <Tag color="green" style={{ margin: 0 }}>담김</Tag>}
                {Number(f.refCnt) > 0 ? (
                  <Tag color="blue" style={{ margin: 0 }}>{f.refCnt}곳</Tag>
                ) : (
                  <Tag style={{ margin: 0 }}>미사용</Tag>
                )}
              </Space>
            </div>
          ))}
        </div>
      )}
      <Pagination
        style={{ marginTop: 16, textAlign: 'right' }}
        align="end"
        current={page}
        pageSize={pageSize}
        total={total}
        showSizeChanger
        onChange={(p, ps) => changePage(p, ps)}
      />
    </Spin>
  )

  const list = (
    <Card
      title="파일"
      extra={
        <Space>
          <Segmented
            value={view}
            onChange={(v) => setView(v as 'grid' | 'list')}
            options={[
              { label: '그리드', value: 'grid' },
              { label: '목록', value: 'list' },
            ]}
          />
          <Upload {...uploadProps}>
            <Button type="primary">업로드</Button>
          </Upload>
          <Popconfirm
            title="고아 파일 정리"
            description="어디에도 연결되지 않은 파일을 지금 정리합니다(라이브러리에 담긴 파일은 제외)."
            onConfirm={runGc}
            okText="정리"
            cancelText="취소"
          >
            <Button loading={busy}>고아 정리</Button>
          </Popconfirm>
        </Space>
      }
    >
      <SearchBar
        fields={[
          {
            type: 'select',
            name: 'filterExtGroup',
            options: [
              { value: 'IMAGE', label: '이미지' },
              { value: 'ETC', label: '이미지 외' },
            ],
          },
          {
            type: 'select',
            name: 'filterUsed',
            options: [
              { value: 'USED', label: '사용 중' },
              { value: 'UNUSED', label: '미사용' },
            ],
          },
          {
            type: 'select',
            name: 'filterLibrary',
            options: [{ value: 'Y', label: '라이브러리만' }],
          },
          { type: 'daterange', name: 'regDt', fromName: 'filterFrom', toName: 'filterTo' },
          { type: 'text', name: 'filterKeyword', placeholder: '파일명' },
        ]}
        onSearch={(v) => search(v)}
      />
      {view === 'grid' ? (
        grid
      ) : (
        <Table<MediaFile>
          rowKey="fileId"
          size="small"
          scroll={{ x: 'max-content' }}
          columns={columns}
          dataSource={rows}
          loading={loading}
          rowClassName={(r) => (r.fileId === selected?.fileId ? 'ant-table-row-selected' : '')}
          onRow={(r) => ({ onClick: () => setSelected(r), style: { cursor: 'pointer' } })}
          pagination={{ current: page, pageSize, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
        />
      )}
    </Card>
  )

  const detail = (
    <Card
      title="상세"
      extra={
        selected && (
          <Space>
            <Button onClick={() => fileApi.download(selected.fileId!, selected.originalName ?? 'file')}>
              다운로드
            </Button>
            <Button onClick={() => toggleLibrary(selected)}>
              {selected.libraryYn === 'Y' ? '라이브러리에서 빼기' : '라이브러리에 담기'}
            </Button>
            <Popconfirm
              title="파일 삭제"
              description={
                Number(selected.refCnt) > 0
                  ? '이 파일을 쓰는 곳이 있습니다. 지우면 그 화면에서 이미지가 깨집니다.'
                  : '삭제하시겠습니까?'
              }
              onConfirm={() => remove(selected)}
              okText="삭제"
              cancelText="취소"
              okButtonProps={{ danger: true }}
            >
              <Button danger>삭제</Button>
            </Popconfirm>
          </Space>
        )
      }
    >
      {!selected ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>파일을 선택하세요.</div>
      ) : (
        <>
          {isImage(selected.ext) && (
            <div style={{ marginBottom: 12 }}>
              <AuthImage fileId={selected.fileId} alt={selected.originalName} size={240} />
            </div>
          )}
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="파일명">{selected.originalName}</Descriptions.Item>
            <Descriptions.Item label="형식">{selected.ext ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="크기">{humanSize(selected.size)}</Descriptions.Item>
            <Descriptions.Item label="등록">{selected.regId} · {selected.regDt}</Descriptions.Item>
            <Descriptions.Item label="라이브러리">
              {selected.libraryYn === 'Y' ? <Tag color="green">담김</Tag> : <Tag>안 담김</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="사용처">
              {refsLoading ? (
                <Spin size="small" />
              ) : refs.length === 0 ? (
                <span style={{ color: '#999' }}>
                  어디에도 쓰이지 않습니다(프로필 사진 포함해서 확인했습니다).
                  {selected.libraryYn !== 'Y' && ' 라이브러리에 담지 않으면 고아 정리 대상입니다.'}
                </span>
              ) : (
                <Space direction="vertical" size={2}>
                  {refs.map((r) => (
                    <span key={`${r.fileType}-${r.mapKey}`}>{refLabel(r)}</span>
                  ))}
                </Space>
              )}
            </Descriptions.Item>
          </Descriptions>
        </>
      )}
    </Card>
  )

  return (
    <Card title="미디어 라이브러리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
    </Card>
  )
}
