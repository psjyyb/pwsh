package com.pwsh.domain.message.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.message.service.MessageService;
import com.pwsh.domain.message.service.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 쪽지 API — 컨트롤러는 매핑·입력검증만, 로직은 {@link MessageService}. 전 경로 로그인 필요(본인 기준).
 * 조회는 {path}로 분기: ''=대화 목록, Thread=특정 상대 대화, UnreadCnt=전체 안읽음.
 */
@RestController
@RequestMapping("/api/adm/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/selectMessageList{path}.do")
    public ApiResponse<?> selectList(@PathVariable String path, @RequestBody MessageVO vo) {
        if ("Thread".equals(path)) {
            Validate.required(vo.getOtherHandle(), "상대");
            return ApiResponse.ok(messageService.selectThread(vo.getOtherHandle()));
        }
        if ("UnreadCnt".equals(path)) {
            return ApiResponse.ok(messageService.unreadCnt());
        }
        return ApiResponse.ok(messageService.selectConvList());
    }

    /**
     * 실시간 알림 스트림(SSE) — 새 쪽지·알림이 생기면 "종류"만 밀어준다(본문 없음).
     * 클라이언트는 이벤트를 받고 기존 조회 API로 실제 데이터를 가져간다(인가는 조회 API가 담당).
     *
     * <p>EventSource가 아니라 fetch로 소비하도록 POST로 둔다 — EventSource는 Authorization 헤더를
     * 보낼 수 없어 토큰을 URL에 실어야 하고, 그러면 접근 로그·리퍼러에 토큰이 남는다.
     * ApiResponse로 감싸지 않는 유일한 엔드포인트(스트림이므로).
     */
    @PostMapping(value = "/selectMessageListStream.do", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return messageService.subscribe();
    }

    /** 쪽지 보내기 */
    @PostMapping("/insertMessage.do")
    public ApiResponse<Void> insert(@RequestBody MessageVO vo) {
        messageService.send(vo);
        return ApiResponse.ok();
    }

    /** 상대와의 대화 읽음 처리 (상대는 handle) */
    @PostMapping("/updateMessageRead.do")
    public ApiResponse<Void> updateRead(@RequestBody MessageVO vo) {
        Validate.required(vo.getOtherHandle(), "상대");
        messageService.markRead(vo.getOtherHandle());
        return ApiResponse.ok();
    }

    /** 대화 삭제(내 화면에서만, 상대는 handle) */
    @PostMapping("/deleteMessage.do")
    public ApiResponse<Void> delete(@RequestBody MessageVO vo) {
        Validate.required(vo.getOtherHandle(), "상대");
        messageService.deleteConv(vo.getOtherHandle());
        return ApiResponse.ok();
    }
}
