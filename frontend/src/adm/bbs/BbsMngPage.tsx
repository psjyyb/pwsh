import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Card, Form, Input, Popconfirm, Space, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { fileApi } from '../../api/file'
import type { FileMeta } from '../../api/file'
import FileUpload from '../../common/adm/components/FileUpload'
import GalleryImages from '../../common/adm/components/GalleryImages'
import type { GalleryItem } from '../../common/adm/components/GalleryImages'
import DateField from '../../common/adm/components/DateField'
import YnSelect from '../../common/adm/components/YnSelect'
import RichTextEditor from '../../common/adm/components/RichTextEditor'
import type { RichTextEditorHandle } from '../../common/adm/components/RichTextEditor'
import { extractEditorImageIds } from '../../common/util/editorImages'
import SafeHtml from '../../common/SafeHtml'
import { bbsinfoApi } from '../bbsinfo/bbsinfo.api'
import type { Bbsinfo } from '../bbsinfo/bbsinfo.api'
import { BBS_LIST_URL, bbsApi } from './bbs.api'
import type { Bbs } from './bbs.api'
import { commentApi } from './comment.api'
import type { Comment } from './comment.api'

type Mode = 'list' | 'view' | 'write'
const FILE_LOC = 'BBS' // 첨부파일
const IMG_LOC = 'BBS_IMG' // 갤러리 사진(캡션 포함)
const EDITOR_LOC = 'BBS_EDITOR' // 본문 에디터 삽입 이미지(고아 추적용)

/**
 * 관리자 게시글 관리 — 메뉴 link_url=/adm/bbs/{bbsinfoId} 로 진입(게시판별 전용 메뉴).
 * bbsinfoId는 마운트 시 경로에서 고정(탭 keep-alive 시 다른 탭 경로와 섞이지 않도록).
 * 목록/등록/수정/삭제 + 에디터 본문 + 첨부 + 댓글 + 비밀글/공지. 관리자는 전체 글 관리.
 */
export default function BbsMngPage() {
  const [bbsinfoId] = useState(() => {
    const seg = window.location.pathname.split('/').filter(Boolean) // ['adm','bbs','1']
    return seg[2] ?? ''
  })
  const [board, setBoard] = useState<Bbsinfo | null>(null)
  const [mode, setMode] = useState<Mode>('list')

  // 목록
  const [rows, setRows] = useState<Bbs[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [keyword, setKeyword] = useState('')

  // 상세
  const [post, setPost] = useState<Bbs | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [commentText, setCommentText] = useState('')
  const [viewFiles, setViewFiles] = useState<FileMeta[]>([])

  // 작성/수정
  const [form] = Form.useForm()
  const [editKey, setEditKey] = useState<string | null>(null)
  const [fileIds, setFileIds] = useState<string[]>([])
  const [fileMetas, setFileMetas] = useState<FileMeta[]>([]) // 편집 시 기존 첨부 메타
  const [galleryItems, setGalleryItems] = useState<GalleryItem[]>([]) // 갤러리 작성용 사진+캡션
  const [viewGallery, setViewGallery] = useState<GalleryItem[]>([]) // 갤러리 상세용
  const editorRef = useRef<RichTextEditorHandle>(null)
  const [context, setContext] = useState('')

  const pageSize = Number(board?.listCnt) || 10
  const isGallery = board?.bbsinfoCd === 'BBSINFO004'
  const isFaq = board?.bbsinfoCd === 'BBSINFO002'
  const isQna = board?.bbsinfoCd === 'BBSINFO003'
  const noticeWatch = Form.useWatch('noticeYn', form)
  // NEW 표시: 게시판 설정 new_cnt(일) 이내 등록글이면 true
  const isNew = (regDt?: string) => {
    const n = Number(board?.newCnt)
    if (!n || !regDt) return false
    const t = new Date(`${regDt}T00:00:00`).getTime()
    return !Number.isNaN(t) && Date.now() - t <= n * 86400000
  }

  const loadList = useCallback(
    async (p = 1, kw = keyword) => {
      if (!bbsinfoId) return
      try {
        const res = await apiPost<ListResult<Bbs>>(BBS_LIST_URL, { bbsinfoId, pageNo: p, pageSize, filterKeyword: kw })
        setRows(res.list)
        setTotal(res.totalCount)
        setPageNo(p)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '목록 조회 실패')
      }
    },
    [bbsinfoId, pageSize, keyword],
  )

  useEffect(() => {
    if (!bbsinfoId) return
    bbsinfoApi.view(bbsinfoId).then(setBoard).catch(() => setBoard(null))
    loadList(1, '')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bbsinfoId])

  const openView = async (bbsId: string) => {
    try {
      if (bbsinfoId) bbsinfoApi.view(bbsinfoId).then(setBoard).catch(() => {})
      // 관리자(MEM02)는 서버가 비밀글도 바로 열어줌
      const p = await bbsApi.view(bbsId)
      setPost(p)
      setComments(await commentApi.list(bbsId))
      if (isGallery) {
        const imgs = await fileApi.listByMap(bbsId, IMG_LOC)
        setViewGallery(imgs.map((f) => ({ fileId: f.fileId!, fileOrgNm: f.fileOrgNm, fileDesc: f.fileDesc })))
        setViewFiles([])
      } else {
        setViewGallery([])
        setViewFiles(board?.fileYn === 'Y' ? await fileApi.listByMap(bbsId, FILE_LOC) : [])
      }
      setMode('view')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회 실패')
    }
  }

  const openWrite = () => {
    // 탭 keep-alive로 설정이 낡을 수 있어 진입 시 최신 게시판 설정 재조회(첨부/공지 노출 기준)
    if (bbsinfoId) bbsinfoApi.view(bbsinfoId).then(setBoard).catch(() => {})
    setEditKey(null)
    setFileIds([])
    setFileMetas([])
    setGalleryItems([])
    setContext('')
    form.resetFields()
    setMode('write')
  }
  const openEdit = async () => {
    if (!post) return
    if (bbsinfoId) bbsinfoApi.view(bbsinfoId).then(setBoard).catch(() => {})
    setEditKey(post.rowId!)
    setContext(post.context ?? '')
    form.setFieldsValue({ title: post.title, noticeYn: post.noticeYn ?? 'N', noticeStartDt: post.noticeStartDt, noticeEndDt: post.noticeEndDt })
    if (isGallery) {
      const imgs = await fileApi.listByMap(post.rowId!, IMG_LOC)
      setGalleryItems(imgs.map((f) => ({ fileId: f.fileId!, fileOrgNm: f.fileOrgNm, fileDesc: f.fileDesc })))
      setFileMetas([])
      setFileIds([])
    } else if (board?.fileYn === 'Y') {
      const metas = await fileApi.listByMap(post.rowId!, FILE_LOC)
      setFileMetas(metas)
      setFileIds(metas.map((f) => f.fileId!))
      setGalleryItems([])
    } else {
      setFileMetas([])
      setFileIds([])
      setGalleryItems([])
    }
    setMode('write')
  }

  const saveBbs = async () => {
    const v = await form.validateFields()
    const html = editorRef.current?.getHTML() ?? context
    // 갤러리는 사진 1장 이상 필수(본문 선택), 그 외는 본문 필수
    if (isGallery) {
      if (galleryItems.length === 0) {
        message.warning('사진을 1장 이상 등록하세요.')
        return
      }
    } else if (!html || !html.replace(/<[^>]*>/g, '').trim()) {
      message.warning('내용을 입력하세요.')
      return
    }
    try {
      // 관리자는 비밀글을 설정/해제하지 않음 — 신규는 'N', 수정은 기존값 유지(비번도 COALESCE로 보존)
      const payload = { bbsinfoId, title: v.title, context: html, secretYn: editKey ? (post?.secretYn ?? 'N') : 'N', noticeYn: v.noticeYn ?? 'N', noticeStartDt: v.noticeStartDt, noticeEndDt: v.noticeEndDt }
      const id = editKey ? (await bbsApi.update({ ...payload, rowId: editKey }), editKey) : await bbsApi.insert(payload)
      if (isGallery) {
        await fileApi.saveMapping(id, IMG_LOC, galleryItems.map((i) => i.fileId), galleryItems.map((i) => i.fileDesc ?? ''))
      } else if (board?.fileYn === 'Y') {
        await fileApi.saveMapping(id, FILE_LOC, fileIds)
      }
      // 본문 에디터 삽입 이미지 추적(고아 판별용)
      await fileApi.saveMapping(id, EDITOR_LOC, extractEditorImageIds(html))
      message.success('저장되었습니다.')
      setMode('list')
      loadList(editKey ? pageNo : 1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장 실패')
    }
  }

  const removeBbs = async () => {
    if (!post) return
    try {
      await bbsApi.remove(post.rowId!)
      message.success('삭제되었습니다.')
      setMode('list')
      loadList(1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '삭제 실패')
    }
  }

  const addComment = async () => {
    if (!post || !commentText.trim()) return
    try {
      await commentApi.insert(post.rowId!, commentText.trim())
      setCommentText('')
      setComments(await commentApi.list(post.rowId!))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '댓글 등록 실패')
    }
  }
  const removeComment = async (rowId: string) => {
    try {
      await commentApi.remove(rowId)
      if (post) setComments(await commentApi.list(post.rowId!))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '댓글 삭제 실패')
    }
  }

  // ===== 목록 (유형별 컬럼 분화) =====
  if (mode === 'list') {
    const regCol = { title: '작성자', width: 130, render: (_: unknown, r: Bbs) => r.regNm || r.regId }
    const dtCol = { title: '작성일', dataIndex: 'regDt' as const, width: 120 }
    let columns: TableColumnsType<Bbs>
    if (isGallery) {
      // 갤러리: 썸네일 미리보기 중심
      columns = [
        {
          title: '썸네일',
          width: 96,
          render: (_, r) =>
            r.fileId ? (
              <img src={`/api/pub/image/${r.fileId}`} alt="" style={{ width: 72, height: 48, objectFit: 'cover', borderRadius: 4, border: '1px solid #eee' }} />
            ) : (
              <span style={{ color: '#ccc' }}>없음</span>
            ),
        },
        { title: '제목', render: (_, r) => (<span>{r.title}{isNew(r.regDt) && <Tag color="green" style={{ marginLeft: 6 }}>NEW</Tag>}</span>) },
        regCol,
        dtCol,
      ]
    } else if (isQna) {
      // 1:1 문의: 답변 상태 중심
      columns = [
        {
          title: '제목',
          render: (_, r) => (
            <span>
              {r.secretYn === 'Y' && <span style={{ marginRight: 4 }}>🔒</span>}
              {r.title}
              {Number(r.commentCnt) > 0 && <span style={{ color: '#1677ff', marginLeft: 6 }}>[{r.commentCnt}]</span>}
            </span>
          ),
        },
        { title: '답변상태', width: 90, render: (_, r) => (Number(r.commentCnt) > 0 ? <Tag color="blue">답변완료</Tag> : <Tag>답변대기</Tag>) },
        regCol,
        dtCol,
      ]
    } else if (isFaq) {
      // FAQ: 질문 목록
      columns = [
        { title: '질문', render: (_, r) => (<span><b style={{ color: '#1677ff', marginRight: 6 }}>Q</b>{r.title}</span>) },
        regCol,
        dtCol,
      ]
    } else {
      // 일반: 공지/조회 중심
      columns = [
        {
          title: '제목',
          render: (_, r) => (
            <span>
              {r.noticeEff === 'Y' && <Tag color="red">공지</Tag>}
              {r.secretYn === 'Y' && <span style={{ marginRight: 4 }}>🔒</span>}
              {r.title}
              {isNew(r.regDt) && <Tag color="green" style={{ marginLeft: 6 }}>NEW</Tag>}
              {Number(r.commentCnt) > 0 && <span style={{ color: '#1677ff', marginLeft: 6 }}>[{r.commentCnt}]</span>}
            </span>
          ),
        },
        regCol,
        { title: '좋아요', width: 70, align: 'center', render: (_, r) => Number(r.goodCnt) || 0 },
        { title: '조회', dataIndex: 'viewCnt', width: 70 },
        dtCol,
      ]
    }
    return (
      <Card
        title={`${board?.bbsinfoNm ?? '게시판'} 관리`}
        extra={<Button type="primary" onClick={openWrite}>{isFaq ? 'FAQ 등록' : '글쓰기'}</Button>}
      >
        <Space style={{ marginBottom: 12 }}>
          <Input.Search
            placeholder={isFaq ? '질문 검색' : '제목 검색'}
            allowClear
            style={{ width: 260 }}
            onSearch={(v) => { setKeyword(v); loadList(1, v) }}
          />
        </Space>
        <Table<Bbs>
          rowKey="rowId"
          scroll={{ x: 'max-content' }}
          size="small"
          columns={columns}
          dataSource={rows}
          onRow={(r) => ({ onClick: () => openView(r.rowId!), style: { cursor: 'pointer' } })}
          pagination={{ current: pageNo, pageSize: pageSize, total, onChange: (p) => loadList(p) }}
        />
      </Card>
    )
  }

  // ===== 상세 =====
  if (mode === 'view' && post) {
    return (
      <Card
        title={post.title}
        extra={
          <Space>
            <Button onClick={openEdit}>수정</Button>
            <Popconfirm title="삭제하시겠습니까?" onConfirm={removeBbs} okText="삭제" cancelText="취소">
              <Button danger>삭제</Button>
            </Popconfirm>
            <Button onClick={() => { setMode('list'); loadList(pageNo) }}>목록</Button>
          </Space>
        }
      >
        <div style={{ color: '#888', fontSize: 13, marginBottom: 12 }}>
          작성자 {post.regNm || post.regId} · {post.regDt} · 조회 {post.viewCnt}
          {post.secretYn === 'Y' && <Tag style={{ marginLeft: 8 }}>비밀글</Tag>}
          {isQna && <Tag color={comments.length > 0 ? 'blue' : undefined} style={{ marginLeft: 8 }}>{comments.length > 0 ? '답변완료' : '답변대기'}</Tag>}
        </div>
        {isGallery && viewGallery.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16, marginBottom: 16 }}>
            {viewGallery.map((g) => (
              <figure key={g.fileId} style={{ margin: 0 }}>
                <img src={`/api/pub/image/${g.fileId}`} alt={g.fileDesc ?? ''} style={{ width: '100%', borderRadius: 6, display: 'block', border: '1px solid #eee' }} />
                {g.fileDesc && <figcaption style={{ marginTop: 6, color: '#555', fontSize: 13, textAlign: 'center' }}>{g.fileDesc}</figcaption>}
              </figure>
            ))}
          </div>
        )}
        <SafeHtml className="toastui-editor-contents" style={{ minHeight: isGallery ? 0 : 120 }} html={post.context ?? ''} />

        {viewFiles.length > 0 && (
          <div style={{ marginTop: 16, borderTop: '1px solid #eee', paddingTop: 8 }}>
            <b>첨부파일</b>
            <ul style={{ margin: '6px 0' }}>
              {viewFiles.map((f) => (
                <li key={f.fileId}>
                  <a onClick={() => fileApi.download(f.fileId!, f.fileOrgNm ?? 'file')}>{f.fileOrgNm}</a>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* FAQ는 질문=제목·답변=본문 구조라 댓글 영역 없음. 1:1은 '답변'으로 표기 */}
        {!isFaq && (
          <div style={{ marginTop: 20, borderTop: '1px solid #eee', paddingTop: 12 }}>
            <b>{isQna ? `답변 ${comments.length}` : `댓글 ${comments.length}`}</b>
            {comments.map((c) => (
              <div key={c.rowId} style={{ padding: '8px 0', borderBottom: '1px solid #f5f5f5' }}>
                <div style={{ fontSize: 12, color: '#888' }}>
                  {c.regNm || c.regId} · {c.regDt}
                  <a style={{ marginLeft: 8 }} onClick={() => removeComment(c.rowId!)}>삭제</a>
                </div>
                <div style={{ whiteSpace: 'pre-wrap' }}>{c.context}</div>
              </div>
            ))}
            <Space.Compact style={{ width: '100%', marginTop: 8 }}>
              <Input.TextArea
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                autoSize={{ minRows: 1, maxRows: 4 }}
                placeholder={isQna ? '답변을 입력하세요' : '댓글을 입력하세요'}
              />
              <Button type="primary" onClick={addComment}>등록</Button>
            </Space.Compact>
          </div>
        )}
      </Card>
    )
  }

  // ===== 작성/수정 =====
  const editTitle = editKey ? '수정' : '등록'
  return (
    <Card
      title={isFaq ? `FAQ ${editTitle}` : `게시글 ${editTitle}`}
      extra={
        <Space>
          <Button onClick={() => setMode('list')}>목록</Button>
          <Button type="primary" onClick={saveBbs}>저장</Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={{ noticeYn: 'N' }}>
        <Form.Item name="title" label={isFaq ? '질문' : '제목'} rules={[{ required: true, message: (isFaq ? '질문' : '제목') + '을 입력하세요.' }]}>
          <Input />
        </Form.Item>
        {isGallery && (
          <Form.Item label="사진 (사진마다 설명 입력)" required>
            <GalleryImages
              key={`${editKey ?? 'new'}-gallery`}
              initialItems={galleryItems}
              maxSizeMb={Number(board?.fileSize) || undefined}
              onChange={setGalleryItems}
            />
          </Form.Item>
        )}
        <Form.Item label={isFaq ? '답변' : isGallery ? '설명 (선택)' : '내용'} required={!isGallery}>
          <RichTextEditor
            key={`${editKey ?? 'new'}-editor`}
            ref={editorRef}
            initialHtml={context}
            uploadImage={fileApi.uploadImage}
          />
        </Form.Item>
        {board?.noticeYn === 'Y' && (
          <Space size={8} wrap align="baseline">
            <Form.Item name="noticeYn" label="공지"><YnSelect /></Form.Item>
            {noticeWatch === 'Y' && <Form.Item name="noticeStartDt" label="공지 시작"><DateField allowClear /></Form.Item>}
            {noticeWatch === 'Y' && <Form.Item name="noticeEndDt" label="공지 종료"><DateField allowClear /></Form.Item>}
          </Space>
        )}
        {board?.fileYn === 'Y' && !isFaq && !isGallery && (
          <Form.Item label="첨부파일">
            <FileUpload
              key={`${editKey ?? 'new'}-file`}
              initialFiles={fileMetas}
              maxCount={Number(board?.fileCnt) || undefined}
              maxSizeMb={Number(board?.fileSize) || undefined}
              onChange={setFileIds}
            />
          </Form.Item>
        )}
      </Form>
    </Card>
  )
}
