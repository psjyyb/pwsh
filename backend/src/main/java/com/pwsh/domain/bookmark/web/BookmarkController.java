package com.pwsh.domain.bookmark.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.bookmark.service.BookmarkService;
import com.pwsh.domain.bookmark.service.BookmarkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 북마크 API — 매핑·입력검증만, 로직은 {@link BookmarkService}. 전 경로 로그인 필요(본인 기준).
 * 조회 {path}: ''=내 북마크 목록(targetType별), Ids=내가 북마크한 id 목록.
 */
@RestController
@RequestMapping("/api/adm/bookmark")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/selectBookmarkList{path}.do")
    public ApiResponse<?> selectList(@PathVariable String path, @RequestBody BookmarkVO vo) {
        if ("Ids".equals(path)) {
            return ApiResponse.ok(bookmarkService.selectMyIds(vo.getTargetType()));
        }
        return ApiResponse.ok(bookmarkService.selectMyList(vo.getTargetType()));
    }

    /** 북마크 토글(추가/취소) → {markedYn} */
    @PostMapping("/updateBookmarkToggle.do")
    public ApiResponse<BookmarkVO> toggle(@RequestBody BookmarkVO vo) {
        Validate.required(vo.getTargetType(), "대상 유형");
        Validate.required(vo.getTargetId(), "대상");
        return ApiResponse.ok(bookmarkService.toggle(vo.getTargetType(), vo.getTargetId()));
    }
}
