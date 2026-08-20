package com.pwsh.domain.member.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.PasswordPolicy;
import com.pwsh.common.util.StringUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.member.service.MemberService;
import com.pwsh.domain.member.service.MemberVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 — 컨트롤러는 매핑·입력검증만, 로직은 {@link MemberService}.
 * selectMemberList{variant}: ''=목록 / AuthGroup=사용자의 권한그룹ID 목록
 * updateMember{variant}: ''=정보수정 / Password=비번변경 / AuthGroup=권한그룹 매핑 저장 / ForceLogout=강제 로그아웃
 */
@RestController
@RequestMapping("/api/adm/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** 목록 계열: ''=페이징목록 / AuthGroup=권한그룹ID 목록 */
    @RequestMapping("/selectMemberList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable(name = "variant", required = false) String variant,
                                     @RequestBody(required = false) MemberVO searchVO) {
        MemberVO vo = searchVO == null ? new MemberVO() : searchVO;
        if ("AuthGroup".equals(variant)) {
            return ApiResponse.ok(memberService.selectAuthGroupIds(vo));
        }
        int totalCount = memberService.selectListTotalCount(vo);
        return ApiResponse.ok(Map.of(
                "list", memberService.selectList(vo),
                "totalCount", totalCount,
                "page", PageUtil.of(vo.getPageNo(), vo.getPageSize(), totalCount)));
    }

    @RequestMapping("/selectMemberView.do")
    public ApiResponse<MemberVO> selectView(@RequestBody MemberVO searchVO) {
        return ApiResponse.ok(memberService.selectView(searchVO));
    }

    @RequestMapping("/insertMember.do")
    public ApiResponse<Void> insert(@RequestBody MemberVO searchVO) {
        Validate.required(searchVO.getMemberId(), "아이디");
        Validate.required(searchVO.getMemberName(), "이름");
        Validate.required(searchVO.getTypeCd(), "회원유형");
        PasswordPolicy.validate(searchVO.getPassword()); // 복잡도 정책(인코딩 전 원문)
        memberService.insert(searchVO);
        return ApiResponse.ok();
    }

    /** 수정. variant: Password=비번변경 / AuthGroup=권한그룹 매핑 / 빈값=정보수정 */
    @RequestMapping("/updateMember{variant}.do")
    public ApiResponse<Void> update(@PathVariable(name = "variant", required = false) String variant,
                                    @RequestBody MemberVO searchVO) {
        if ("Password".equals(variant)) {
            PasswordPolicy.validate(searchVO.getPassword());
            memberService.updatePassword(searchVO);
        } else if ("AuthGroup".equals(variant)) {
            memberService.saveAuthGroup(searchVO);
        } else if ("ForceLogout".equals(variant)) {
            Validate.required(searchVO.getMemberId(), "사용자");
            memberService.forceLogout(searchVO);
        } else if ("Status".equals(variant)) { // 제재: 정지(STATUS03)/해제(STATUS01)
            Validate.required(searchVO.getMemberId(), "사용자");
            Validate.required(searchVO.getStatusCd(), "상태");
            memberService.updateStatus(searchVO);
        } else if (StringUtil.isEmpty(variant)) {
            Validate.required(searchVO.getMemberName(), "이름");
            Validate.required(searchVO.getTypeCd(), "회원유형");
            memberService.updateInfo(searchVO);
        }
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteMember.do")
    public ApiResponse<Void> delete(@RequestBody MemberVO searchVO) {
        memberService.delete(searchVO);
        return ApiResponse.ok();
    }
}
