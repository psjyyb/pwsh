/**
 * 공통 포맷 유틸 (adm/gen 공용) — 숫자·날짜·전화번호 표시 포맷을 한 곳에서 관리.
 * 표시(뷰)용 포맷 함수 모음 — 값 저장은 원본(숫자/ISO) 유지.
 */

/** 천단위 콤마 (1234567 → "1,234,567") */
export function formatNumber(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return ''
  const n = typeof value === 'string' ? Number(value) : value
  if (Number.isNaN(n)) return String(value)
  return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 날짜 포맷 (기본 YYYY-MM-DD). Date·타임스탬프·문자열 허용. 파싱 실패 시 원본 문자열 반환. */
export function formatDate(value: Date | string | number | null | undefined, sep = '-'): string {
  if (value === null || value === undefined || value === '') return ''
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return [y, m, day].join(sep)
}

/** 날짜+시간 (YYYY-MM-DD HH:mm) */
export function formatDateTime(value: Date | string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return ''
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${formatDate(d)} ${hh}:${mm}`
}

/** 전화번호 하이픈 (01012345678 → 010-1234-5678, 0212345678 → 02-1234-5678) */
export function formatPhone(value: string | null | undefined): string {
  const nums = (value ?? '').replace(/[^0-9]/g, '')
  if (!nums) return ''
  if (nums.startsWith('02')) {
    // 서울(02): 지역번호 2자리
    if (nums.length <= 2) return nums
    if (nums.length <= 5) return `${nums.slice(0, 2)}-${nums.slice(2)}`
    if (nums.length <= 9) return `${nums.slice(0, 2)}-${nums.slice(2, 5)}-${nums.slice(5)}`
    return `${nums.slice(0, 2)}-${nums.slice(2, 6)}-${nums.slice(6, 10)}`
  }
  // 그 외(휴대폰/지역번호 3자리)
  if (nums.length <= 3) return nums
  if (nums.length <= 7) return `${nums.slice(0, 3)}-${nums.slice(3)}`
  if (nums.length <= 10) return `${nums.slice(0, 3)}-${nums.slice(3, 6)}-${nums.slice(6)}`
  return `${nums.slice(0, 3)}-${nums.slice(3, 7)}-${nums.slice(7, 11)}`
}

/** 하이픈/공백 등 제거하고 숫자만 (010-1234-5678 → 01012345678) — DB 저장용 */
export function unformatPhone(value: string | null | undefined): string {
  return (value ?? '').replace(/[^0-9]/g, '')
}

/** 콤마 제거하고 숫자만 (1,000,000 → 1000000) — DB 저장용 */
export function unformatNumber(value: string | null | undefined): string {
  return (value ?? '').replace(/[^0-9]/g, '')
}

/** 파일 크기 (1536 → "1.5 KB") */
export function formatFileSize(bytes: number | string | null | undefined): string {
  const n = typeof bytes === 'string' ? Number(bytes) : bytes
  if (n === null || n === undefined || Number.isNaN(n)) return ''
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = n / 1024
  let i = 0
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}
