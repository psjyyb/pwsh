import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Card, Checkbox, Empty, Form, Input, Popconfirm, Space, Spin, Table, Tag, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { apiPost } from '../../api/http'
import type { ListResult } from '../../api/http'
import { getClaims, isAdmin } from '../../auth/token'
import { fileApi } from '../../api/file'
import type { FileMeta } from '../../api/file'
import FileUpload from '../../common/adm/components/FileUpload'
import GalleryImages from '../../common/adm/components/GalleryImages'
import type { GalleryItem } from '../../common/adm/components/GalleryImages'
import DateField from '../../common/adm/components/DateField'
import YnSelect from '../../common/adm/components/YnSelect'
import RichTextEditor from '../../common/adm/components/RichTextEditor'
import SafeHtml from '../../common/SafeHtml'
import type { RichTextEditorHandle } from '../../common/adm/components/RichTextEditor'
import { hasViewedRecently, markViewed } from '../../common/util/bbsView'
import { extractEditorImageIds } from '../../common/util/editorImages'
import type { Bbsinfo } from '../../adm/bbsinfo/bbsinfo.api'
import { BBS_LIST_URL, bbsApi } from '../../adm/bbs/bbs.api'
import type { Bbs } from '../../adm/bbs/bbs.api'
import { commentApi } from '../../adm/bbs/comment.api'
import type { Comment } from '../../adm/bbs/comment.api'

type Mode = 'list' | 'view' | 'write'
const FILE_LOC = 'BBS' // 첨부파일
const IMG_LOC = 'BBS_IMG' // 갤러리 사진(캡션 포함)
const EDITOR_LOC = 'BBS_EDITOR' // 본문 에디터 삽입 이미지(고아 추적용)

/**
 * 표준 게시판 스킨 — 일반(BBSINFO001)/갤러리(004)/1:1문의(003) 공용.
 * 유형별 차이: 갤러리=카드목록+대표이미지, 1:1=비밀글 강제+답변상태(댓글=답변).
 */
export default function StandardBoard({ board }: { board: Bbsinfo }) {
  const bbsinfoId = board.dbKey
  const isGallery = board.bbsinfoCd === 'BBSINFO004'
  const isQna = board.bbsinfoCd === 'BBSINFO003'

  const [mode, setMode] = useState<Mode>('list')
  const [rows, setRows] = useState<Bbs[]>([])
  const [total, setTotal] = useState(0)
  const [pageIndex, setPageIndex] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const [post, setPost] = useState<Bbs | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [commentText, setCommentText] = useState('')
  const [viewFiles, setViewFiles] = useState<FileMeta[]>([])

  const [form] = Form.useForm()
  const [editKey, setEditKey] = useState<string | null>(null)
  const [replyTo, setReplyTo] = useState<Bbs | null>(null) // 답글 작성 시 원글(비어있으면 일반 글)
  const [fileIds, setFileIds] = useState<string[]>([])
  const [fileMetas, setFileMetas] = useState<FileMeta[]>([])
  const [galleryItems, setGalleryItems] = useState<GalleryItem[]>([]) // 갤러리 작성용 사진+캡션
  const [viewGallery, setViewGallery] = useState<GalleryItem[]>([]) // 갤러리 상세 표시용
  const editorRef = useRef<RichTextEditorHandle>(null)
  const [context, setContext] = useState('')

  const size = Number(board.listCnt) || 10
  const meId = getClaims()?.sub
  const canEdit = (regId?: string) => isAdmin() || (!!regId && regId === meId)
  const isNew = (regDt?: string) => {
    const n = Number(board.newCnt)
    if (!n || !regDt) return false
    const t = new Date(`${regDt}T00:00:00`).getTime()
    return !Number.isNaN(t) && Date.now() - t <= n * 86400000
  }
  const secretWatch = Form.useWatch('secretYn', form)
  const noticeWatch = Form.useWatch('noticeYn', form)

  const loadList = useCallback(
    async (p = 1, kw = keyword) => {
      if (!bbsinfoId) return
      setLoading(true)
      try {
        const res = await apiPost<ListResult<Bbs>>(BBS_LIST_URL, { bbsinfoId, pageIndex: p, size, searchKeyword: kw })
        setRows(res.list)
        setTotal(res.totCnt)
        setPageIndex(p)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '목록 조회 실패')
      } finally {
        setLoading(false)
      }
    },
    [bbsinfoId, size, keyword],
  )

  useEffect(() => {
    setMode('list')
    loadList(1, '')
    setKeyword('')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bbsinfoId])

  const openView = async (bbsId: string) => {
    try {
      const countUp = !hasViewedRecently(bbsId)
      let p = await bbsApi.view(bbsId, undefined, countUp)
      if (p?.secretLocked === 'Y') {
        const pw = window.prompt(isQna ? '작성자와 관리자만 열람할 수 있습니다. 비밀번호를 입력하세요.' : '비밀글입니다. 비밀번호를 입력하세요.')
        if (!pw) return
        p = await bbsApi.view(bbsId, pw, countUp)
        if (p?.secretLocked === 'Y') {
          message.error('열람할 수 없습니다. (비밀번호 불일치)')
          return
        }
      }
      if (countUp) markViewed(bbsId)
      setPost(p)
      setComments(await commentApi.list(bbsId))
      if (isGallery) {
        const imgs = await fileApi.listByMap(bbsId, IMG_LOC)
        setViewGallery(imgs.map((f) => ({ fileId: f.fileId!, fileOrgNm: f.fileOrgNm, fileDesc: f.fileDesc })))
        setViewFiles([])
      } else {
        setViewGallery([])
        setViewFiles(board.fileYn === 'Y' ? await fileApi.listByMap(bbsId, FILE_LOC) : [])
      }
      setMode('view')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '조회 실패')
    }
  }

  const openWrite = () => {
    setEditKey(null)
    setReplyTo(null)
    setFileIds([])
    setFileMetas([])
    setGalleryItems([])
    setContext('')
    form.resetFields()
    setMode('write')
  }
  /** 답글 작성 — 원글(post)을 부모로, 같은 게시판에 depth+1로 등록(제목만 RE: 프리필). */
  const openReply = () => {
    if (!post) return
    setEditKey(null)
    setReplyTo(post)
    setFileIds([])
    setFileMetas([])
    setGalleryItems([])
    setContext('')
    form.resetFields()
    form.setFieldsValue({ title: `RE: ${post.title ?? ''}` })
    setMode('write')
  }
  const openEdit = async () => {
    if (!post) return
    setEditKey(post.dbKey!)
    setReplyTo(null)
    setContext(post.context ?? '')
    form.setFieldsValue({
      title: post.title,
      secretYn: post.secretYn === 'Y',
      bbsPw: undefined,
      noticeYn: post.noticeYn ?? 'N',
      noticeStartDt: post.noticeStartDt,
      noticeEndDt: post.noticeEndDt,
    })
    if (isGallery) {
      const imgs = await fileApi.listByMap(post.dbKey!, IMG_LOC)
      setGalleryItems(imgs.map((f) => ({ fileId: f.fileId!, fileOrgNm: f.fileOrgNm, fileDesc: f.fileDesc })))
      setFileMetas([])
      setFileIds([])
    } else if (board.fileYn === 'Y') {
      const metas = await fileApi.listByMap(post.dbKey!, FILE_LOC)
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
    // 갤러리는 사진 1장 이상 필수(본문은 선택), 그 외는 본문 필수
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
      const payload = {
        bbsinfoId,
        title: v.title,
        context: html,
        secretYn: isQna ? 'Y' : v.secretYn ? 'Y' : 'N', // 1:1은 항상 비밀글
        bbsPw: !isQna && v.secretYn ? (v.bbsPw ?? '') : '',
        noticeYn: isQna ? 'N' : v.noticeYn ?? 'N',
        noticeStartDt: v.noticeStartDt,
        noticeEndDt: v.noticeEndDt,
        ...(replyTo && !editKey ? { pBbsId: replyTo.dbKey } : {}), // 답글: 원글 id 전송(서버가 게시판·depth 상속)
      }
      const id = editKey ? (await bbsApi.update({ ...payload, dbKey: editKey }), editKey) : await bbsApi.insert(payload)
      if (isGallery) {
        await fileApi.saveMapping(id, IMG_LOC, galleryItems.map((i) => i.fileId), galleryItems.map((i) => i.fileDesc ?? ''))
      } else if (board.fileYn === 'Y') {
        await fileApi.saveMapping(id, FILE_LOC, fileIds)
      }
      // 본문 에디터 삽입 이미지 추적(고아 판별용)
      await fileApi.saveMapping(id, EDITOR_LOC, extractEditorImageIds(html))
      message.success('저장되었습니다.')
      setMode('list')
      loadList(editKey ? pageIndex : 1)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장 실패')
    }
  }

  const removeBbs = async () => {
    if (!post) return
    try {
      await bbsApi.remove(post.dbKey!)
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
      await commentApi.insert(post.dbKey!, commentText.trim())
      setCommentText('')
      setComments(await commentApi.list(post.dbKey!))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '댓글 등록 실패')
    }
  }
  const removeComment = async (dbKey: string) => {
    try {
      await commentApi.remove(dbKey)
      if (post) setComments(await commentApi.list(post.dbKey!))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '댓글 삭제 실패')
    }
  }

  const titleCell = (r: Bbs) => {
    const depth = Number(r.bbsDepth) || 0
    return (
      <span style={{ paddingLeft: depth * 20 }}>
        {depth > 0 && <span style={{ color: '#999', marginRight: 4 }}>↳</span>}
        {r.noticeEff === 'Y' && <Tag color="red">공지</Tag>}
        {r.secretYn === 'Y' && <span style={{ marginRight: 4 }}>🔒</span>}
        {r.title}
        {isNew(r.regDt) && <Tag color="green" style={{ marginLeft: 6 }}>NEW</Tag>}
        {Number(r.commentCnt) > 0 && <span style={{ color: '#1677ff', marginLeft: 6 }}>[{r.commentCnt}]</span>}
      </span>
    )
  }

  // ===== 목록 =====
  if (mode === 'list') {
    const search = (
      <Space style={{ marginBottom: 12 }}>
        <Input.Search placeholder="제목 검색" allowClear style={{ width: 260 }} onSearch={(v) => { setKeyword(v); loadList(1, v) }} />
      </Space>
    )
    // 갤러리: 카드 그리드
    const galleryBody =
      rows.length === 0 ? (
        <Empty description="등록된 글이 없습니다." />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16 }}>
          {rows.map((r) => (
            <div
              key={r.dbKey}
              onClick={() => openView(r.dbKey!)}
              style={{ cursor: 'pointer', border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden' }}
            >
              <div style={{ height: 140, background: '#fafafa', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {r.fileId ? (
                  <img src={`/api/pub/image/${r.fileId}`} alt={r.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ color: '#bbb' }}>No Image</span>
                )}
              </div>
              <div style={{ padding: 8 }}>
                <div style={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {r.title}
                  {isNew(r.regDt) && <Tag color="green" style={{ marginLeft: 4 }}>NEW</Tag>}
                </div>
                <div style={{ color: '#999', fontSize: 12 }}>{r.regDt}</div>
              </div>
            </div>
          ))}
        </div>
      )

    const columns: TableColumnsType<Bbs> = [
      { title: '제목', render: (_, r) => titleCell(r) },
      ...(isQna
        ? [{
            title: '답변상태',
            width: 90,
            render: (_: unknown, r: Bbs) =>
              Number(r.commentCnt) > 0 ? <Tag color="blue">답변완료</Tag> : <Tag>답변대기</Tag>,
          }]
        : []),
      { title: '작성자', width: 130, render: (_: unknown, r: Bbs) => r.regNm || r.regId },
      { title: '조회', dataIndex: 'viewCnt', width: 70 },
      { title: '작성일', dataIndex: 'regDt', width: 120 },
    ]

    return (
      <Card title={board.bbsinfoNm ?? '게시판'} extra={<Button type="primary" onClick={openWrite}>글쓰기</Button>}>
        {search}
        {isGallery ? (
          <>
            <Spin spinning={loading}>{galleryBody}</Spin>
            <div style={{ textAlign: 'right', marginTop: 12 }}>
              <Space>
                <Button size="small" disabled={pageIndex <= 1} onClick={() => loadList(pageIndex - 1)}>이전</Button>
                <span style={{ color: '#999' }}>{pageIndex} / {Math.max(1, Math.ceil(total / size))}</span>
                <Button size="small" disabled={pageIndex >= Math.ceil(total / size)} onClick={() => loadList(pageIndex + 1)}>다음</Button>
              </Space>
            </div>
          </>
        ) : (
          <>
            <Table<Bbs>
              rowKey="dbKey"
              size="small"
              loading={loading}
              columns={columns}
              dataSource={rows}
              onRow={(r) => ({
                onClick: () => {
                  // 1:1은 본인·관리자만 열람(남의 문의는 목록에서 열지 않음)
                  if (isQna && !isAdmin() && r.regId !== meId) {
                    message.info('본인 문의만 열람할 수 있습니다.')
                    return
                  }
                  openView(r.dbKey!)
                },
                style: { cursor: 'pointer' },
              })}
              pagination={false}
            />
            {/* 원글+답글(스레드)이 한 페이지에 섞여 오므로 Table 자체 페이징 대신 원글 기준 페이저 사용 */}
            <div style={{ textAlign: 'right', marginTop: 12 }}>
              <Space>
                <Button size="small" disabled={pageIndex <= 1} onClick={() => loadList(pageIndex - 1)}>이전</Button>
                <span style={{ color: '#999' }}>{pageIndex} / {Math.max(1, Math.ceil(total / size))}</span>
                <Button size="small" disabled={pageIndex >= Math.ceil(total / size)} onClick={() => loadList(pageIndex + 1)}>다음</Button>
              </Space>
            </div>
          </>
        )}
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
            {!isGallery && !isQna && <Button onClick={openReply}>답글</Button>}
            {canEdit(post.regId) && <Button onClick={openEdit}>수정</Button>}
            {canEdit(post.regId) && (
              <Popconfirm title="삭제하시겠습니까?" onConfirm={removeBbs} okText="삭제" cancelText="취소">
                <Button danger>삭제</Button>
              </Popconfirm>
            )}
            <Button onClick={() => { setMode('list'); loadList(pageIndex) }}>목록</Button>
          </Space>
        }
      >
        <div style={{ color: '#888', fontSize: 13, marginBottom: 12 }}>
          작성자 {post.regNm || post.regId} · {post.regDt} · 조회 {post.viewCnt}
        </div>
        {isGallery && viewGallery.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16, marginBottom: 16 }}>
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

        <div style={{ marginTop: 20, borderTop: '1px solid #eee', paddingTop: 12 }}>
          <b>{isQna ? `답변 ${comments.length}` : `댓글 ${comments.length}`}</b>
          {comments.map((c) => (
            <div key={c.dbKey} style={{ padding: '8px 0', borderBottom: '1px solid #f5f5f5' }}>
              <div style={{ fontSize: 12, color: '#888' }}>
                {c.regNm || c.regId} · {c.regDt}
                {canEdit(c.regId) && <a style={{ marginLeft: 8 }} onClick={() => removeComment(c.dbKey!)}>삭제</a>}
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
      </Card>
    )
  }

  // ===== 작성/수정 =====
  return (
    <Card
      title={editKey ? '글 수정' : replyTo ? '답글 작성' : '글 작성'}
      extra={
        <Space>
          <Button onClick={() => setMode('list')}>목록</Button>
          <Button type="primary" onClick={saveBbs}>저장</Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={{ noticeYn: 'N', secretYn: false }}>
        <Form.Item name="title" label="제목" rules={[{ required: true, message: '제목을 입력하세요.' }]}>
          <Input />
        </Form.Item>
        {isGallery && (
          <Form.Item label="사진 (사진마다 설명 입력)" required>
            <GalleryImages
              key={`${editKey ?? 'new'}-gallery`}
              initialItems={galleryItems}
              maxSizeMb={Number(board.fileSize) || undefined}
              onChange={setGalleryItems}
            />
          </Form.Item>
        )}
        <Form.Item label={isGallery ? '설명 (선택)' : '내용'} required={!isGallery}>
          <RichTextEditor key={`${editKey ?? 'new'}-editor`} ref={editorRef} initialHtml={context} uploadImage={fileApi.uploadImage} />
        </Form.Item>
        {isQna ? (
          <div style={{ color: '#888', fontSize: 12, marginBottom: 8 }}>※ 1:1 문의는 작성자와 관리자만 볼 수 있습니다.</div>
        ) : (
          <Space size={16} wrap align="baseline">
            <Form.Item name="secretYn" valuePropName="checked" label="비밀글">
              <Checkbox>작성자·관리자만 열람</Checkbox>
            </Form.Item>
            {secretWatch && (
              <Form.Item
                name="bbsPw"
                label={editKey ? '비밀번호 (변경 시에만 입력)' : '비밀번호'}
                rules={editKey ? [] : [{ required: true, message: '비밀번호를 입력하세요.' }]}
              >
                <Input.Password style={{ width: 200 }} autoComplete="new-password" />
              </Form.Item>
            )}
          </Space>
        )}
        {!isQna && board.noticeYn === 'Y' && (
          <Space size={8} wrap align="baseline">
            <Form.Item name="noticeYn" label="공지"><YnSelect /></Form.Item>
            {noticeWatch === 'Y' && <Form.Item name="noticeStartDt" label="공지 시작"><DateField allowClear /></Form.Item>}
            {noticeWatch === 'Y' && <Form.Item name="noticeEndDt" label="공지 종료"><DateField allowClear /></Form.Item>}
          </Space>
        )}
        {!isGallery && board.fileYn === 'Y' && (
          <Form.Item label="첨부파일">
            <FileUpload
              key={`${editKey ?? 'new'}-file`}
              initialFiles={fileMetas}
              maxCount={Number(board.fileCnt) || undefined}
              maxSizeMb={Number(board.fileSize) || undefined}
              onChange={setFileIds}
            />
          </Form.Item>
        )}
      </Form>
    </Card>
  )
}
