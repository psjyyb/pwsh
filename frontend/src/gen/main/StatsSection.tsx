import { useCountUp } from '../hooks/useCountUp'
import Reveal from './Reveal'

/**
 * 통계 숫자 한 칸 — 화면에 들어올 때 0에서 올라간다.
 * 컴포넌트로 뽑은 이유: 훅은 조건·반복 안에서 호출할 수 없어 항목마다 컴포넌트가 필요하다.
 */
function StatItem({ label, value, suffix }: { label: string; value: number; suffix?: string }) {
  const [n, ref] = useCountUp(value)
  return (
    <li>
      <span className="label">{label}</span>
      <strong className="num" ref={ref as React.RefObject<HTMLElement>}>
        {n.toLocaleString()}
        {suffix && <i>{suffix}</i>}
      </strong>
    </li>
  )
}

/**
 * 소개 + 통계 패널.
 *
 * <p>★ 수치는 <b>전부 실제 집계</b>다 — 메인이 이미 조회한 데이터(도감·모집·게시글)에서 세서
 * 넘긴다. 보여주기용 숫자를 상수로 박지 않는다(틀린 숫자는 없는 숫자보다 나쁘다).
 * 그래서 별도 통계 API도 만들지 않았다 — 관리자 통계(`/adm/stats`)는 관리자 전용이고,
 * 여기서 필요한 값은 이미 내려온 목록에서 계산할 수 있다.
 *
 * <p>'취미 담기'는 회원이 취미를 담은 <b>횟수 합계</b>다(한 사람이 여러 취미를 담으면 여러 번
 * 세어진다). 라벨을 '멤버'로 쓰면 회원 수로 읽혀 사실과 달라지므로 담기로 표기한다.
 */
export default function StatsSection({
  hobbyCount,
  recruitOpenCount,
  savedCount,
  postCount,
}: {
  hobbyCount: number
  recruitOpenCount: number
  savedCount: number
  postCount: number
}) {
  return (
    <section className="gen-section">
      <div className="gen-wfix">
        <Reveal dir="left">
          <span className="gen-eyebrow">WHY 취만사</span>
          <h2 className="gen-sec-title">
            취미를 고르고, 사람을 만나고,
            <br />
            기록으로 남기는 곳
          </h2>
          <p className="gen-lead">
            도감에서 취미를 고르면 그 취미의 게시판과 모집이 한 곳에 모여 있습니다.
            <br />
            신청부터 단체 대화, 참석 기록과 후기까지 한 흐름으로 이어집니다.
          </p>
        </Reveal>

        <Reveal dir="right" delay={100}>
          <ul className="gen-stat-nums">
            <StatItem label="취미" value={hobbyCount} />
            <StatItem label="모집 중" value={recruitOpenCount} />
            <StatItem label="취미 담기" value={savedCount} />
            <StatItem label="이야기" value={postCount} />
          </ul>
        </Reveal>
      </div>
    </section>
  )
}
