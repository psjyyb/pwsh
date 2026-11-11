import { Alert } from 'antd'
import type { MailSendResult } from '../../../adm/maillog/maillog.api'

/**
 * 메일 발송 결과 표시(템플릿 테스트 발송·메일 설정 점검 공통).
 * SKIP(발송 꺼짐)과 FAIL(SMTP 오류 등)을 구분해 보여 준다 — 둘 다 "안 갔다"지만 고칠 곳이 다르다.
 */
export default function MailResultAlert({ result }: { result: MailSendResult }) {
  if (result.statusCd === 'SUCCESS') {
    return (
      <Alert
        type="success"
        showIcon
        message="발송했습니다."
        description={result.errorMsg ? `경고: ${result.errorMsg}` : '받은편지함(스팸함 포함)에서 확인하세요.'}
      />
    )
  }
  if (result.statusCd === 'SKIP') {
    return (
      <Alert
        type="warning"
        showIcon
        message="메일 발송이 꺼져 있어 실제로 보내지 않았습니다."
        description={`${result.errorMsg ?? ''} — 운영 서버는 MAIL_ENABLED=true와 MAIL_USERNAME·MAIL_PASSWORD를 설정해야 합니다. 이력에는 남았습니다.`}
      />
    )
  }
  return <Alert type="error" showIcon message="발송에 실패했습니다." description={result.errorMsg ?? '사유 미상 — 메일발송이력을 확인하세요.'} />
}
