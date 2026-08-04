package com.pwsh.domain.message.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.message.service.MessageService;
import com.pwsh.domain.message.service.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            Validate.required(vo.getOtherId(), "상대");
            return ApiResponse.ok(messageService.selectThread(vo.getOtherId()));
        }
        if ("UnreadCnt".equals(path)) {
            return ApiResponse.ok(messageService.unreadCnt());
        }
        return ApiResponse.ok(messageService.selectConvList());
    }

    /** 쪽지 보내기 */
    @PostMapping("/insertMessage.do")
    public ApiResponse<Void> insert(@RequestBody MessageVO vo) {
        messageService.send(vo);
        return ApiResponse.ok();
    }

    /** 상대와의 대화 읽음 처리 */
    @PostMapping("/updateMessageRead.do")
    public ApiResponse<Void> updateRead(@RequestBody MessageVO vo) {
        Validate.required(vo.getOtherId(), "상대");
        messageService.markRead(vo.getOtherId());
        return ApiResponse.ok();
    }

    /** 대화 삭제(내 화면에서만) */
    @PostMapping("/deleteMessage.do")
    public ApiResponse<Void> delete(@RequestBody MessageVO vo) {
        Validate.required(vo.getOtherId(), "상대");
        messageService.deleteConv(vo.getOtherId());
        return ApiResponse.ok();
    }
}
