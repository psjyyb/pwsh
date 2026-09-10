package com.pwsh.domain.configitem.web;

import com.pwsh.common.response.ApiResponse;
import com.pwsh.common.util.Validate;
import com.pwsh.domain.configitem.service.ConfigItemService;
import com.pwsh.domain.configitem.service.ConfigItemVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확장 설정 관리 — 컨트롤러는 매핑·입력검증만, 로직은 {@link ConfigItemService}.
 *
 * <p>항목 <b>정의</b>(키·항목명·입력유형)는 개발자가 data.sql로 넣고, 화면에서는 <b>값만</b> 바꾼다.
 * 그래서 insert/delete 엔드포인트를 두지 않는다 — 운영자가 키를 임의로 만들면
 * 그 키를 읽는 코드가 없어 아무 효과가 없는 설정이 쌓인다.
 */
@RestController
@RequestMapping("/api/adm/configitem")
@RequiredArgsConstructor
public class ConfigItemController {

    private final ConfigItemService configItemService;

    @RequestMapping("/selectConfigItemList.do")
    public ApiResponse<List<ConfigItemVO>> selectList(@RequestBody(required = false) ConfigItemVO searchVO) {
        return ApiResponse.ok(configItemService.selectList(searchVO == null ? new ConfigItemVO() : searchVO));
    }

    /** 값 일괄 저장 — 화면이 바뀐 항목들을 한 번에 보낸다. */
    @RequestMapping("/updateConfigItem.do")
    public ApiResponse<Void> update(@RequestBody List<ConfigItemVO> items) {
        if (items != null) {
            for (ConfigItemVO item : items) {
                Validate.required(item.getConfigKey(), "설정 키");
            }
        }
        configItemService.updateValues(items);
        return ApiResponse.ok();
    }
}
