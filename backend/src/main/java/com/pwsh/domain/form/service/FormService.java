package com.pwsh.domain.form.service;

import com.pwsh.common.CommonDAO;
import com.pwsh.common.exception.BusinessException;
import com.pwsh.common.exception.ErrorCode;
import com.pwsh.global.security.GenAccessGuard;
import com.pwsh.global.security.SecurityUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폼(신청·민원·설문) 업무 로직. 컨트롤러는 매핑만, 로직·트랜잭션은 여기(도메인 단일 @Service).
 *
 * <p>세 용도를 한 엔진으로 두는 이유: "문항을 정의하고 → 답을 받고 → 모아 본다"가 똑같다.
 * 따로 만들면 화면·매퍼·검증이 세 벌이 되고, 문항 유형을 하나 늘릴 때마다 세 곳을 고쳐야 한다.
 */
@Service
@RequiredArgsConstructor
public class FormService {

    /** 선택지를 쓰는 문항 유형(단일선택/다중선택/드롭다운) */
    private static final List<String> CHOICE_FIELDS = List.of("FIELD03", "FIELD04", "FIELD05");
    /** 다중선택 문항의 답 구분자 — 값 안에 줄바꿈이 들어갈 수 없는 유형이라 안전하다 */
    private static final String MULTI_SEP = "\n";

    private final CommonDAO commonDAO;
    private final GenAccessGuard genAccessGuard;

    // ===== 폼 정의 =====

    public List<FormVO> selectList(FormVO vo) {
        return commonDAO.selectList("formDAO.selectList", vo);
    }

    public int selectListTotalCount(FormVO vo) {
        return commonDAO.selectOne("formDAO.selectListTotalCount", vo);
    }

    public List<FormVO> selectListCombo(FormVO vo) {
        return commonDAO.selectList("formDAO.selectListCombo", vo);
    }

    /**
     * 폼 + 문항. 관리자 화면과 사용자 응답 화면이 같이 쓴다.
     * 사용자(비관리자)에게는 메뉴 권한을 먼저 확인한다 — 폼 ID 딥링크로 남의 폼을 여는 것을 막는다.
     */
    public FormVO selectView(FormVO vo) {
        FormVO form = commonDAO.selectOne("formDAO.selectView", vo);
        if (form == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        genAccessGuard.checkForm(form.getRowId());
        form.setFields(selectFields(form.getRowId()));
        return form;
    }

    public List<FormFieldVO> selectFields(String formId) {
        FormFieldVO p = new FormFieldVO();
        p.setFormId(formId);
        return commonDAO.selectList("formFieldDAO.selectListByForm", p);
    }

    @Transactional
    public void insert(FormVO vo) {
        commonDAO.insert("formDAO.insert", vo); // useGeneratedKeys → rowId = form_id
        saveFields(vo);
    }

    @Transactional
    public void update(FormVO vo) {
        commonDAO.update("formDAO.update", vo);
        saveFields(vo);
    }

    /**
     * 문항 일괄 저장(화면 한 번 저장 = 요청 1건). 새 문항은 등록, 기존 문항은 수정,
     * 화면에서 빠진 문항은 <b>비활성화</b>한다.
     *
     * <p>지우지 않고 비활성화하는 이유: 이미 들어온 응답이 문항 ID로 붙어 있어서,
     * 물리 삭제하면 과거 응답이 "어느 문항의 답인지 모르는 값"이 된다.
     */
    private void saveFields(FormVO vo) {
        List<FormFieldVO> fields = vo.getFields() == null ? List.of() : vo.getFields();
        List<String> keepIds = new ArrayList<>();
        int sort = 1;
        for (FormFieldVO f : fields) {
            f.setFormId(vo.getRowId());
            f.setSortNo(String.valueOf(sort++));
            normalizeOptions(f);
            if (f.getRowId() == null || f.getRowId().isBlank()) {
                commonDAO.insert("formFieldDAO.insert", f);
            } else {
                commonDAO.update("formFieldDAO.update", f);
            }
            keepIds.add(f.getRowId());
        }
        Map<String, Object> p = new HashMap<>();
        p.put("formId", vo.getRowId());
        p.put("keepIds", keepIds);
        commonDAO.update("formFieldDAO.deactivateRemoved", p);
    }

    /** 선택형이 아닌 문항에 남아 있는 선택지는 지운다(유형을 바꿔도 옛 선택지가 따라다니지 않도록). */
    private void normalizeOptions(FormFieldVO f) {
        if (!CHOICE_FIELDS.contains(f.getFieldCd())) {
            f.setOptions(null);
            return;
        }
        if (optionsOf(f).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "선택형 문항에는 선택지가 필요합니다: " + f.getLabel());
        }
    }

    /** 폼 삭제(논리) + 문항·응답 동반 비활성화. 응답만 남으면 어느 문항의 답인지 알 수 없다. */
    @Transactional
    public void delete(FormVO vo) {
        commonDAO.delete("formDAO.delete", vo);
        commonDAO.update("formDAO.deleteFieldsByForm", vo);
        commonDAO.update("formDAO.deleteAnswersByForm", vo);
    }

    // ===== 응답 =====

    public List<FormAnswerVO> selectAnswerList(FormAnswerVO vo) {
        return commonDAO.selectList("formAnswerDAO.selectList", vo);
    }

    public int selectAnswerListTotalCount(FormAnswerVO vo) {
        return commonDAO.selectOne("formAnswerDAO.selectListTotalCount", vo);
    }

    /** 응답 상세 + 문항별 값. 개인정보 문항이 있는 폼이면 복호화가 걸리고 접근기록에 남는다. */
    public FormAnswerVO selectAnswerView(FormAnswerVO vo) {
        FormAnswerVO answer = commonDAO.selectOne("formAnswerDAO.selectView", vo);
        if (answer == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 클라이언트가 보낸 값이 아니라 폼 정의를 보고 서비스가 정한다
        answer.setHasPrivacy(hasPrivacyField(answer.getFormId()) ? "Y" : "N");
        List<Map<String, Object>> rows = commonDAO.selectList("formAnswerDAO.selectValues", answer);
        answer.setValues(toValueMap(rows, "fieldId"));
        return answer;
    }

    /**
     * 제출. 기간·로그인·중복·필수·선택지까지 서버에서 다시 본다 —
     * 프론트 검증은 실수를 줄여줄 뿐이고, 요청은 화면 없이도 보낼 수 있다.
     */
    @Transactional
    public String submit(FormAnswerVO vo) {
        FormVO key = new FormVO();
        key.setRowId(vo.getFormId());
        FormVO form = commonDAO.selectOne("formDAO.selectView", key);
        if (form == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        genAccessGuard.checkForm(form.getRowId());
        assertOpen(form);

        boolean loginRequired = !"N".equals(form.getLoginYn());
        if (loginRequired && !SecurityUtil.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String memberId = SecurityUtil.isAuthenticated() ? SecurityUtil.getCurrentMemberId() : null;
        assertNotDuplicated(form, memberId);

        List<FormFieldVO> fields = selectFields(form.getRowId());
        Map<String, String> values = vo.getValues() == null ? Map.of() : vo.getValues();

        vo.setMemberId(memberId);
        commonDAO.insert("formAnswerDAO.insert", vo); // useGeneratedKeys → rowId = form_answer_id

        for (FormFieldVO f : fields) {
            String value = trimToNull(values.get(f.getRowId()));
            validate(f, value);
            if (value == null) {
                continue;
            }
            Map<String, Object> p = new HashMap<>();
            p.put("answerId", vo.getRowId());
            p.put("fieldId", f.getRowId());
            p.put("cryptoKey", vo.getCryptoKey());
            // 개인정보 문항의 답만 암호화 컬럼으로 간다 — 그래야 조회가 접근기록에 남는다
            boolean privacy = "Y".equals(f.getPrivacyYn());
            p.put("value", privacy ? null : value);
            p.put("enc", privacy ? value : null);
            commonDAO.insert("formAnswerDAO.insertValue", p);
        }
        return vo.getRowId();
    }

    /** 접수 기간 검사. 비어 있으면 제한 없음(팝업·배너와 같은 규칙). */
    private void assertOpen(FormVO form) {
        String today = LocalDate.now().toString();
        if (notBlank(form.getStartDt()) && today.compareTo(form.getStartDt()) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아직 접수 기간이 아닙니다.");
        }
        if (notBlank(form.getEndDt()) && today.compareTo(form.getEndDt()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "접수가 마감되었습니다.");
        }
    }

    /** 1인 1회 제한. 비로그인 제출은 제출자를 특정할 수 없어 적용하지 않는다(그 경우 로그인 필수로 둔다). */
    private void assertNotDuplicated(FormVO form, String memberId) {
        if ("Y".equals(form.getMultiYn()) || memberId == null) {
            return;
        }
        FormAnswerVO p = new FormAnswerVO();
        p.setFormId(form.getRowId());
        p.setMemberId(memberId);
        Integer cnt = commonDAO.selectOne("formAnswerDAO.selectCountByMember", p);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 제출하셨습니다.");
        }
    }

    /** 필수 여부 + 선택형 값이 실제 선택지인지. 화면을 거치지 않은 요청을 막는다. */
    private void validate(FormFieldVO f, String value) {
        if (value == null) {
            if ("Y".equals(f.getRequiredYn())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, f.getLabel() + "은(는) 필수입니다.");
            }
            return;
        }
        if (!CHOICE_FIELDS.contains(f.getFieldCd())) {
            return;
        }
        List<String> options = optionsOf(f);
        List<String> picked = "FIELD04".equals(f.getFieldCd())
                ? Arrays.stream(value.split(MULTI_SEP)).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of(value);
        for (String one : picked) {
            if (!options.contains(one)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        f.getLabel() + ": 선택할 수 없는 값입니다.");
            }
        }
    }

    @Transactional
    public void updateAnswerStatus(FormAnswerVO vo) {
        commonDAO.update("formAnswerDAO.updateStatus", vo);
    }

    @Transactional
    public void deleteAnswer(FormAnswerVO vo) {
        commonDAO.delete("formAnswerDAO.delete", vo);
        commonDAO.delete("formAnswerDAO.deleteValues", vo);
    }

    // ===== 집계 / 내려받기 =====

    /**
     * 문항별 집계. 선택형은 선택지별 응답 수, 그 외는 응답 수만 낸다.
     * <b>개인정보 문항은 집계 대상이 아니다</b> — 이름·연락처를 세어 볼 일이 없고,
     * 통계를 볼 때마다 복호화가 일어나면 개인정보 접근기록이 의미 없이 쌓인다.
     */
    public List<Map<String, Object>> stats(String formId) {
        FormAnswerVO p = new FormAnswerVO();
        p.setFormId(formId);
        List<Map<String, Object>> rows = commonDAO.selectList("formAnswerDAO.selectStatValues", p);

        Map<String, List<String>> byField = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String fieldId = String.valueOf(r.get("fieldId"));
            String value = r.get("value") == null ? "" : String.valueOf(r.get("value"));
            byField.computeIfAbsent(fieldId, k -> new ArrayList<>()).add(value);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (FormFieldVO f : selectFields(formId)) {
            if ("Y".equals(f.getPrivacyYn())) {
                continue;
            }
            List<String> answers = byField.getOrDefault(f.getRowId(), List.of());
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("fieldId", f.getRowId());
            stat.put("label", f.getLabel());
            stat.put("fieldCd", f.getFieldCd());
            stat.put("answerCnt", answers.size());
            if (CHOICE_FIELDS.contains(f.getFieldCd())) {
                stat.put("options", countOptions(f, answers));
            }
            out.add(stat);
        }
        return out;
    }

    /** 선택지별 응답 수(선택되지 않은 선택지도 0으로 남긴다 — 빠지면 "없는 보기"처럼 보인다). */
    private List<Map<String, Object>> countOptions(FormFieldVO f, List<String> answers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String option : optionsOf(f)) {
            counts.put(option, 0);
        }
        for (String answer : answers) {
            for (String one : answer.split(MULTI_SEP)) {
                String key = one.trim();
                if (counts.containsKey(key)) {
                    counts.put(key, counts.get(key) + 1);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counts.forEach((label, cnt) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", label);
            m.put("cnt", cnt);
            out.add(m);
        });
        return out;
    }

    /**
     * 내려받기용 전체 응답(문항 값 포함). 개인정보 문항이 있으면 복호화가 걸리므로
     * 이 조회는 <b>개인정보 접근기록에 남는다</b> — 대량 내려받기야말로 남겨야 할 행위다.
     */
    public Map<String, Object> export(String formId) {
        FormAnswerVO p = new FormAnswerVO();
        p.setFormId(formId);
        p.setPageNo(1);
        p.setPageSize(Integer.MAX_VALUE);
        List<FormAnswerVO> answers = commonDAO.selectList("formAnswerDAO.selectList", p);

        p.setHasPrivacy(hasPrivacyField(formId) ? "Y" : "N");
        List<Map<String, Object>> valueRows = commonDAO.selectList("formAnswerDAO.selectValuesByForm", p);
        Map<String, Map<String, String>> byAnswer = new LinkedHashMap<>();
        for (Map<String, Object> r : valueRows) {
            byAnswer.computeIfAbsent(String.valueOf(r.get("answerId")), k -> new LinkedHashMap<>())
                    .put(String.valueOf(r.get("fieldId")),
                            r.get("value") == null ? "" : String.valueOf(r.get("value")));
        }
        for (FormAnswerVO a : answers) {
            a.setValues(byAnswer.getOrDefault(a.getRowId(), Map.of()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fields", selectFields(formId));
        out.put("list", answers);
        return out;
    }

    // ===== 공통 =====

    private boolean hasPrivacyField(String formId) {
        return selectFields(formId).stream().anyMatch(f -> "Y".equals(f.getPrivacyYn()));
    }

    /** 선택지 문자열 → 줄 단위 목록(빈 줄 제거). */
    private List<String> optionsOf(FormFieldVO f) {
        if (f.getOptions() == null || f.getOptions().isBlank()) {
            return List.of();
        }
        return Arrays.stream(f.getOptions().split("\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private Map<String, String> toValueMap(List<Map<String, Object>> rows, String keyName) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            out.put(String.valueOf(r.get(keyName)), r.get("value") == null ? "" : String.valueOf(r.get("value")));
        }
        return out;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
