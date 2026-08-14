package com.pwsh.domain.recruit.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.recruit.service.RecruitChatVO;
import com.pwsh.domain.recruit.service.RecruitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 모임 단체 대화 (모집의 하위 엔티티, 신청(RecruitApplyController) 패턴의 peer 컨트롤러).
 * 로직·인가는 {@link RecruitService}(모집 도메인 단일 서비스)에 위임 — 주최자 + 수락된 참여자만 읽고 쓴다.
 * 수정(update)은 없다 — 오간 말을 나중에 바꾸면 대화 기록의 신뢰가 깨지므로 삭제(논리)만 허용한다.
 */
@RestController
@RequestMapping("/api/adm/recruitChat")
@RequiredArgsConstructor
public class RecruitChatController {

    private final RecruitService recruitService;

    @RequestMapping("/selectRecruitChatList.do")
    public ApiResponse<List<RecruitChatVO>> selectList(@RequestBody RecruitChatVO searchVO) {
        Validate.required(searchVO.getRecruitId(), "모집");
        return ApiResponse.ok(recruitService.selectChatList(searchVO));
    }

    /**
     * 실시간 스트림(SSE) — 새 대화가 등록되면 "recruitchat"만 밀어준다(본문 없음).
     * 클라이언트는 이벤트를 받고 위 목록 API로 다시 가져간다(인가는 목록 API가 담당).
     * 쪽지 스트림과 같은 허브라서 알림·쪽지 이벤트도 같이 오며, 클라이언트가 이름으로 골라 쓴다.
     */
    @PostMapping(value = "/selectRecruitChatListStream.do", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return recruitService.subscribeChat();
    }

    @RequestMapping("/insertRecruitChat.do")
    public ApiResponse<Void> insert(@RequestBody RecruitChatVO searchVO) {
        Validate.required(searchVO.getRecruitId(), "모집");
        Validate.required(searchVO.getContent(), "내용");
        recruitService.chatInsert(searchVO);
        return ApiResponse.ok();
    }

    @RequestMapping("/deleteRecruitChat.do")
    public ApiResponse<Void> delete(@RequestBody RecruitChatVO searchVO) {
        Validate.required(searchVO.getDbKey(), "대화");
        recruitService.chatDelete(searchVO);
        return ApiResponse.ok();
    }
}
