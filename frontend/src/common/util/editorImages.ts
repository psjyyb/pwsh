/**
 * 에디터 본문 HTML에서 업로드 이미지의 file_id 추출.
 * 이미지는 `/api/pub/image/{id}` 형태로 삽입되므로 그 id들을 수집(중복 제거).
 * 게시글 저장 시 이 id들을 file_ref(file_type='POST_EDITOR')로 매핑해 두면
 * 본문 이미지도 "어느 글이 참조 중인지" 추적되어(=고아 판별 가능) 후속 정리가 가능해진다.
 */
export function extractEditorImageIds(html: string): string[] {
  if (!html) return []
  const ids = new Set<string>()
  const re = /\/api\/pub\/image\/(\d+)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(html)) !== null) ids.add(m[1])
  return Array.from(ids)
}
