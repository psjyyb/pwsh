package com.pwsh.common;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공통 DAO. sql_id(네임스페이스.구문ID) 문자열로 MyBatis를 범용 실행. (표준 CMS CommonDAO 계승, 별도 프레임워크 제거)
 */
@Repository
@RequiredArgsConstructor
public class CommonDAO {

    private final SqlSessionTemplate sqlSession;

    public <T> List<T> selectList(String sqlId, Object param) {
        return sqlSession.selectList(sqlId, param);
    }

    public <T> T selectOne(String sqlId, Object param) {
        return sqlSession.selectOne(sqlId, param);
    }

    public int insert(String sqlId, Object param) {
        return sqlSession.insert(sqlId, param);
    }

    public int update(String sqlId, Object param) {
        return sqlSession.update(sqlId, param);
    }

    public int delete(String sqlId, Object param) {
        return sqlSession.delete(sqlId, param);
    }
}
