package com.pwsh.domain.hobby.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.hobby.service.HobbyService;
import com.pwsh.domain.hobby.service.HobbyVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 취미 카탈로그. 컨트롤러는 매핑·입력검증만, 로직은 {@link HobbyService}.
 * 목록/조회 공개(입문자 도감·허브), 등록/수정/삭제는 관리자(취미관리 메뉴 권한).
 */
@RestController
@RequestMapping("/api/adm/hobby")
@RequiredArgsConstructor
public class HobbyController {

    private final HobbyService hobbyService;

    @RequestMapping("/selectHobbyList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody HobbyVO searchVO) {
        int totCnt = hobbyService.selectListTotCnt(searchVO);
        return ApiResponse.ok(Map.of(
                "list", hobbyService.selectList(searchVO),
                "totCnt", totCnt,
                "page", PageUtil.of(searchVO.getPageIndex(), searchVO.getSize(), totCnt)));
    }

    @RequestMapping("/selectHobbyView.do")
    public ApiResponse<HobbyVO> selectView(@RequestBody HobbyVO searchVO) {
        return ApiResponse.ok(hobbyService.selectView(searchVO));
    }

    @RequestMapping("/insertHobby.do")
    public ApiResponse<String> insert(@RequestBody HobbyVO searchVO) {
        Validate.required(searchVO.getHobbyNm(), "취미명");
        hobbyService.insert(searchVO);
        return ApiResponse.ok(searchVO.getRowId());
    }

    @RequestMapping("/updateHobby.do")
    public ApiResponse<Void> update(@RequestBody HobbyVO searchVO) {
        Validate.required(searchVO.getHobbyNm(), "취미명");
        hobbyService.update(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteHobby.do")
    public ApiResponse<Void> delete(@RequestBody HobbyVO searchVO) {
        hobbyService.delete(searchVO);
        return ApiResponse.ok();
    }
}
