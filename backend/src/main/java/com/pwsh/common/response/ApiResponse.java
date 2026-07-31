package com.pwsh.common.response;

/**
 * 표준 API 응답 봉투. 모든 REST 응답을 감싼다.
 * 성공: { success:true, data:..., error:null }
 * 실패: { success:false, data:null, error:{code,message} }
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
