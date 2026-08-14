/**
 * 활동 배지 — 이미 쌓이는 데이터(참석 기록·주최 모집·받은 후기)로만 계산한다.
 * 별도 테이블·집계 배치가 없어 어긋날 여지가 없다(항상 현재 값에서 파생).
 *
 * 규칙은 여기 한 곳에만 둔다 — 프로필·마이페이지가 같은 기준을 쓰게 하려면
 * 화면마다 조건을 흩어놓으면 안 된다.
 */
export interface Badge {
  key: string
  label: string
  color: string
  /** 왜 받았는지 — 툴팁으로 보여준다(기준이 불투명하면 배지는 장식이 된다) */
  desc: string
}

export interface BadgeInput {
  attended: number   // 참석 확인된 횟수
  noshow: number     // 노쇼 횟수
  hosted: number     // 주최한 모집 수
  reviewCnt: number  // 받은 후기 수
  avgRating: number  // 평균 평점(0이면 후기 없음)
}

/** 숫자 파싱(문자열·널 안전) — 서버 VO는 값을 문자열로 준다. */
export function num(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/**
 * 배지 산출. 조건을 넉넉하게 잡아 초반에도 하나씩 달 수 있게 하고,
 * 상위 배지는 실제로 꾸준한 사람만 받도록 간격을 둔다.
 */
export function badgesOf(v: BadgeInput): Badge[] {
  const out: Badge[] = []

  // 참석 — 모임에 실제로 나온 횟수(주최자가 기록한 것만 집계됨)
  if (v.attended >= 20) {
    out.push({ key: 'attend20', label: '모임 고수', color: 'purple', desc: `모임 ${v.attended}회 참석` })
  } else if (v.attended >= 10) {
    out.push({ key: 'attend10', label: '단골', color: 'geekblue', desc: `모임 ${v.attended}회 참석` })
  } else if (v.attended >= 3) {
    out.push({ key: 'attend3', label: '꾸준함', color: 'blue', desc: `모임 ${v.attended}회 참석` })
  } else if (v.attended >= 1) {
    out.push({ key: 'attend1', label: '첫 모임', color: 'cyan', desc: '첫 모임에 참석했어요' })
  }

  // 주최 — 모임을 여는 사람이 커뮤니티를 굴린다
  if (v.hosted >= 10) {
    out.push({ key: 'host10', label: '모임 리더', color: 'volcano', desc: `모집 ${v.hosted}회 주최` })
  } else if (v.hosted >= 3) {
    out.push({ key: 'host3', label: '모임장', color: 'orange', desc: `모집 ${v.hosted}회 주최` })
  } else if (v.hosted >= 1) {
    out.push({ key: 'host1', label: '첫 모임 개설', color: 'gold', desc: '모집을 열어봤어요' })
  }

  // 신뢰 — 참석 이력이 쌓였는데 노쇼가 없을 때만(약속을 지킨다는 신호)
  if (v.attended >= 5 && v.noshow === 0) {
    out.push({ key: 'noNoshow', label: '약속 지킴', color: 'green', desc: `${v.attended}회 참석, 노쇼 없음` })
  }

  // 평점 — 표본이 너무 적으면 신뢰할 수 없으니 3개 이상일 때만
  if (v.reviewCnt >= 3 && v.avgRating >= 4.5) {
    out.push({ key: 'rating', label: '평점 우수', color: 'magenta', desc: `후기 ${v.reviewCnt}개 · 평균 ${v.avgRating}점` })
  }

  return out
}
