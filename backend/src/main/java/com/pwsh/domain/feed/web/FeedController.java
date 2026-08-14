package com.pwsh.domain.feed.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.PageUtil;
import com.pwsh.domain.feed.service.FeedService;
import com.pwsh.domain.feed.service.FeedVO;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 취미 피드 API — 매핑만, 로직·인가는 {@link FeedService}(항상 로그인 본인 기준).
 * 집계 전용 도메인이라 조회 1건뿐(등록/수정/삭제 없음).
 */
@RestController
@RequestMapping("/api/adm/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /** 피드 목록(로그인 필요). body {feedFilter?, pageNo, pageSize} */
    @RequestMapping("/selectFeedList.do")
    public ApiResponse<Map<String, Object>> selectList(@RequestBody FeedVO searchVO) {
        int totalCount = feedService.selectListTotalCount(searchVO);
        Map<String, Object> result = new HashMap<>();
        result.put("list", feedService.selectList(searchVO));
        result.put("totalCount", totalCount);
        result.put("page", PageUtil.of(searchVO.getPageNo(), searchVO.getPageSize(), totalCount));
        // 담은 취미가 없어 비었는지, 취미는 담았지만 글이 없어 비었는지 화면에서 구분하기 위해
        result.put("myHobbyCnt", feedService.selectMyHobbyCnt(searchVO));
        return ApiResponse.ok(result);
    }
}
