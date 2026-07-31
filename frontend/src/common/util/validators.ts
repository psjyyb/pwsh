import type { Rule } from 'antd/es/form'

/**
 * 공통 입력 형식 검증 규칙 (AntD Form.Item rules에 사용).
 *   <Form.Item name="email" rules={fieldRules('email')}> ...
 * 또는 개별 규칙 직접 사용: rules={emailRules}
 * 모두 "값이 있을 때만" 형식 검사(빈 값 허용). 필수 여부는 required 규칙을 따로 추가.
 */

/** 이메일: ~~~@~~~.~~~ (도메인에 점 필수) */
export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export const emailRules: Rule[] = [
  {
    validator: (_, v) =>
      !v || EMAIL_REGEX.test(v)
        ? Promise.resolve()
        : Promise.reject(new Error('올바른 이메일 형식이 아닙니다. (예: example@exam.com)')),
  },
]

/** 전화번호: 숫자 9~11자리(하이픈 무시) */
export const phoneRules: Rule[] = [
  {
    validator: (_, v) => {
      const d = (v ?? '').replace(/\D/g, '')
      return !d || (d.length >= 9 && d.length <= 11)
        ? Promise.resolve()
        : Promise.reject(new Error('전화번호 형식이 올바르지 않습니다.'))
    },
  },
]

/** 생년월일: YYYY-MM-DD */
export const dateRules: Rule[] = [
  {
    validator: (_, v) =>
      !v || /^\d{4}-\d{2}-\d{2}$/.test(v)
        ? Promise.resolve()
        : Promise.reject(new Error('날짜 형식(YYYY-MM-DD)이 올바르지 않습니다.')),
  },
]

/**
 * 필드명(name/id)에 따라 형식 규칙 자동 선택.
 * name에 email/phone/tel/mobile/birth 등이 포함되면 해당 규칙을 반환.
 * 새 형식이 필요하면 여기 매핑만 추가하면 전 화면에 공통 적용됨.
 */
export function fieldRules(name?: string): Rule[] {
  const n = (name ?? '').toLowerCase()
  if (n.includes('email')) return emailRules
  if (n.includes('phone') || n.includes('tel') || n.includes('mobile')) return phoneRules
  if (n.includes('birth') || n.includes('date') || n.endsWith('dt')) return dateRules
  return []
}
