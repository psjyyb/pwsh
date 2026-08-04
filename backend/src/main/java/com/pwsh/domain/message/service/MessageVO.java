package com.pwsh.domain.message.service;

import com.pwsh.common.BaseVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 쪽지(t_message). 대화는 (sender,receiver) 쌍의 메시지 모음.
 * myId=현재 로그인 회원(서버 강제), otherId=대화 상대.
 */
@Getter
@Setter
public class MessageVO extends BaseVO {
    private String senderId;
    private String receiverId;
    private String content;
    private String readYn;

    // 조회/파라미터 보조
    private String myId;       // 현재 로그인 회원(서버 세팅, 로그인 ID)
    private String otherId;    // 대화 상대(서버 내부용 로그인 ID — 응답에는 넣지 않음)
    private String otherHandle;// 대화 상대 공개 식별자(handle) — 클라이언트가 쓰는 대화 키
    private String receiverHandle; // 발송 요청 파라미터(받는 사람 handle)
    private String otherNm;    // 상대 닉네임(조회)
    private String otherFileId;// 상대 프로필 사진 file_id(조회)
    private String lastContent;// 대화 목록: 최근 메시지 내용
    private String lastDt;     // 대화 목록: 최근 메시지 시각
    private String unreadCnt;  // 대화 목록: 상대와의 안읽음 수
    private String lastMine;   // 최근 메시지가 내가 보낸 것인지(Y/N)
    private String mine;       // 스레드: 이 메시지가 내가 보낸 것인지(Y/N)
}
