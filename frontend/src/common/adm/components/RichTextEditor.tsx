import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'
import Editor from '@toast-ui/editor'
import '@toast-ui/editor/dist/toastui-editor.css'
// 초기 로드(setHTML) 이후 등록하므로, 사용자 편집에만 onChange가 호출됨

/**
 * 리치 텍스트 에디터 (Toast UI Editor, MIT 무료). HTML 본문 편집용.
 * 바닐라 인스턴스를 직접 마운트(React 래퍼는 peer가 React17이라 미사용).
 * ref로 getHTML/setHTML 노출 — 상세폼에서 초기 HTML 주입 + 저장 시 HTML 추출.
 * 이미지: uploadImage prop 주입 시 파일 업로드+공개 URL 삽입, 미주입 시 기본(base64 인라인).
 */
export interface RichTextEditorHandle {
  getHTML: () => string
  setHTML: (html: string) => void
}

interface Props {
  height?: string
  /** 마운트 시 주입할 초기 HTML(수정 시 기존 본문). 값이 바뀌면 key로 재마운트 권장. */
  initialHtml?: string
  /** 사용자가 편집했을 때 호출(초기 로드는 제외) */
  onChange?: () => void
  /** 본문 이미지 업로드 → 삽입할 URL 반환. 미지정 시 에디터 기본(base64) 동작. */
  uploadImage?: (blob: Blob, fileName: string) => Promise<string>
}

const RichTextEditor = forwardRef<RichTextEditorHandle, Props>(({ height = '480px', initialHtml = '', onChange, uploadImage }, ref) => {
  const elRef = useRef<HTMLDivElement>(null)
  const editorRef = useRef<Editor | null>(null)
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  const uploadRef = useRef(uploadImage)
  uploadRef.current = uploadImage

  useEffect(() => {
    if (!elRef.current) return
    const options: ConstructorParameters<typeof Editor>[0] = {
      el: elRef.current,
      height,
      initialEditType: 'wysiwyg',
      previewStyle: 'vertical',
      initialValue: '',
      language: 'ko-KR',
      autofocus: false, // 마운트 시 자동 포커스로 페이지가 에디터 위치로 스크롤되는 현상 방지(사용자가 스크롤해야 이동)
    }
    // uploadImage 주입 시에만 훅 등록(서버 업로드 후 URL 삽입).
    // 미주입 시 훅을 아예 두지 않아 에디터 기본 동작(base64 인라인) 유지.
    if (uploadRef.current) {
      options.hooks = {
        addImageBlobHook: (blob: Blob | File, callback: (url: string, altText?: string) => void) => {
          const upload = uploadRef.current!
          const name = blob instanceof File ? blob.name : 'image'
          upload(blob, name)
            .then((url) => callback(url, name))
            .catch(() => callback('', ''))
          return false
        },
      }
    }
    const editor = new Editor(options)
    // 두 번째 인자 cursorToEnd=false: 초기 HTML 주입 시 커서를 끝으로 옮기지 않음
    // (기본 true면 포커스가 잡히며 페이지가 에디터 위치로 스크롤됨 — 상세 진입 시 자동 스크롤 원인)
    if (initialHtml) editor.setHTML(initialHtml, false)
    // 초기 로드 직후 상태를 기준선으로 저장 → 이후 내용이 기준선과 달라질 때(=실제 편집)만 onChange
    const baseline = editor.getHTML()
    editor.on('change', () => {
      if (editor.getHTML() !== baseline) onChangeRef.current?.()
    })
    editorRef.current = editor
    return () => {
      editor.destroy()
      editorRef.current = null
    }
    // 마운트 시 1회 생성 + 초기 HTML 주입. 다른 글 편집 시 상위에서 key로 재마운트.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useImperativeHandle(ref, () => ({
    getHTML: () => editorRef.current?.getHTML() ?? '',
    setHTML: (html: string) => editorRef.current?.setHTML(html || ''),
  }))

  return <div ref={elRef} />
})

RichTextEditor.displayName = 'RichTextEditor'
export default RichTextEditor
