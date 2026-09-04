package com.pwsh.common.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 커밋 이후에 실행할 작업 등록.
 *
 * <p><b>왜 필요한가</b>: 트랜잭션 안에서 되돌릴 수 없는 외부 작업(파일 물리삭제, 메일 발송, 외부 API 호출)을
 * 하면, 이후 롤백 시 DB는 원복되지만 외부 작업은 되돌아오지 않는다. 파일을 지운 뒤 롤백되면
 * DB에는 파일 행이 살아 있는데 디스크에는 없는 상태가 되고 복구도 안 된다.
 *
 * <p>트랜잭션이 없으면(테스트·배치 등) 즉시 실행한다 — 등록만 해두고 아무 일도 안 하는 것보다 안전하다.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /** 커밋 성공 후 실행. 트랜잭션이 없으면 즉시 실행. */
    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
