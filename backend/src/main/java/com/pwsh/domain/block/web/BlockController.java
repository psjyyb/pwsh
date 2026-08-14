package com.pwsh.domain.block.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.block.service.BlockService;
import com.pwsh.domain.block.service.BlockVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 차단 API — 매핑·입력검증만, 로직은 {@link BlockService}. 전 경로 로그인 필요(본인 기준).
 * 조회 {variant}: ''=차단 목록, Ids=차단한 ID 목록, Check=특정 회원 차단 여부.
 */
@RestController
@RequestMapping("/api/adm/block")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/selectBlockList{variant}.do")
    public ApiResponse<?> selectList(@PathVariable String variant, @RequestBody BlockVO vo) {
        if ("Ids".equals(variant)) {
            return ApiResponse.ok(blockService.selectMyBlockedIds());
        }
        if ("Check".equals(variant)) {
            Validate.required(vo.getBlockedHandle(), "대상 회원");
            return ApiResponse.ok(blockService.isBlocked(vo.getBlockedHandle()) ? "Y" : "N");
        }
        return ApiResponse.ok(blockService.selectMyList());
    }

    /** 차단 토글(차단/해제) → {blockedYn} */
    @PostMapping("/updateBlockToggle.do")
    public ApiResponse<BlockVO> toggle(@RequestBody BlockVO vo) {
        Validate.required(vo.getBlockedHandle(), "대상 회원");
        return ApiResponse.ok(blockService.toggle(vo.getBlockedHandle()));
    }
}
