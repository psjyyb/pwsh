import { useState } from 'react'
import { Button, Card, Checkbox, Form, Input, Popconfirm, Space, Table, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useList } from '../../common/hooks/useList'
import { useSplitForm } from '../../common/hooks/useSplitForm'
import SearchBar from '../../common/adm/components/SearchBar'
import SplitLayout from '../../common/adm/components/SplitLayout'
import CodeSelect from '../../common/adm/components/CodeSelect'
import DateField from '../../common/adm/components/DateField'
import PhoneInput from '../../common/adm/components/PhoneInput'
import { fieldRules } from '../../common/util/validators'
import { USER_LIST_URL, userApi } from './user.api'
import type { User } from './user.api'
import AuthgrpAssignModal from './AuthgrpAssignModal'

/** 사용자 관리 — 분할 마스터-디테일(평면) + 권한그룹 지정 모달. */
export default function UserListPage() {
  const { rows, total, loading, page, size, reload, search, changePage } = useList<User>(USER_LIST_URL)
  const { form, mode, selectedKey, openNew, openRow, remove } = useSplitForm<User>(userApi, reload)
  const [authTarget, setAuthTarget] = useState<User | null>(null)
  const isEdit = mode === 'edit'
  const changePw = Form.useWatch('changePw', form)

  /** 저장 — 정보수정 후, 비밀번호 변경 체크 시에만 비번 별도 갱신 */
  const saveUser = async () => {
    const values = await form.validateFields()
    try {
      if (isEdit) {
        await userApi.update({ ...values, rowId: selectedKey! })
        if (values.changePw && values.userPw) {
          await userApi.changePassword(selectedKey!, values.userPw)
        }
      } else {
        await userApi.insert(values)
      }
      message.success('저장되었습니다.')
      reload()
      // 비밀번호 변경 UI 초기화(체크 해제 + 입력값 제거). 나머지 필드는 저장된 값 유지
      if (isEdit) {
        form.setFieldsValue({ changePw: false, userPw: undefined, userPwConfirm: undefined })
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '저장에 실패했습니다.')
    }
  }

  /** 강제 로그아웃 — 대상 사용자의 발급 토큰을 서버에서 즉시 무효화(token_ver +1) */
  const forceLogout = async () => {
    try {
      await userApi.forceLogout(selectedKey!)
      message.success('해당 사용자를 강제 로그아웃했습니다.')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '강제 로그아웃에 실패했습니다.')
    }
  }

  /** 제재: 정지(STATUS03) / 해제(STATUS01). 정지 시 서버가 세션도 즉시 무효화한다. */
  const changeStatus = async (statusCd: 'STATUS01' | 'STATUS03') => {
    try {
      await userApi.changeStatus(selectedKey!, statusCd)
      message.success(statusCd === 'STATUS03' ? '계정을 정지했습니다. (즉시 접근 차단)' : '정지를 해제했습니다.')
      reload()
      openRow(selectedKey!)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '제재 처리에 실패했습니다.')
    }
  }

  const columns: TableColumnsType<User> = [
    { title: '아이디', dataIndex: 'userId', width: 140 },
    { title: '이름', dataIndex: 'userNm' },
    { title: '회원유형', width: 100, render: (_, r) => r.memCdNm ?? r.memCd },
    { title: '계정상태', width: 90, render: (_, r) => r.statusCdNm ?? r.statusCd },
    { title: '사용', dataIndex: 'useYn', width: 60 },
  ]

  const list = (
    <Card title="목록">
      <SearchBar
        fields={[
          { type: 'text', name: 'searchKeyword', placeholder: '아이디/이름' },
          { type: 'code', name: 'memCd', pCodeId: 'MEM00' },
          { type: 'code', name: 'statusCd', pCodeId: 'STATUS00' },
        ]}
        onSearch={(v) => search(v)}
      />
      <Table<User>
        rowKey="rowId"
        scroll={{ x: 'max-content' }}
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowClassName={(r) => (r.rowId === selectedKey ? 'ant-table-row-selected' : '')}
        onRow={(r) => ({ onClick: () => openRow(r.rowId!), style: { cursor: 'pointer' } })}
        pagination={{ current: page, pageSize: size, total, showSizeChanger: true, onChange: (p, ps) => changePage(p, ps) }}
      />
    </Card>
  )

  const detail = (
    <Card
      title="상세 / 등록 / 수정"
      extra={
        <Space>
          <Button onClick={() => setAuthTarget({ userId: selectedKey!, userNm: form.getFieldValue('userNm') })} disabled={!isEdit}>권한</Button>
          <Popconfirm
            title="강제 로그아웃"
            description="이 사용자의 로그인 세션을 즉시 종료합니다."
            onConfirm={forceLogout}
            okText="로그아웃"
            cancelText="취소"
            disabled={!isEdit}
          >
            <Button disabled={!isEdit}>강제 로그아웃</Button>
          </Popconfirm>
          {/* 제재: 현재 상태에 따라 정지/해제 토글 (정지 시 서버가 세션도 즉시 무효화) */}
          {form.getFieldValue('statusCd') === 'STATUS03' ? (
            <Popconfirm
              title="정지 해제" description="이 계정의 정지를 해제하고 로그인을 허용합니다."
              onConfirm={() => changeStatus('STATUS01')} okText="해제" cancelText="취소" disabled={!isEdit}
            >
              <Button disabled={!isEdit}>정지 해제</Button>
            </Popconfirm>
          ) : (
            <Popconfirm
              title="계정 정지" description="로그인을 차단하고 현재 세션도 즉시 종료합니다."
              onConfirm={() => changeStatus('STATUS03')} okText="정지" okButtonProps={{ danger: true }} cancelText="취소" disabled={!isEdit}
            >
              <Button danger disabled={!isEdit}>계정 정지</Button>
            </Popconfirm>
          )}
          <Button onClick={openNew}>신규</Button>
          <Button type="primary" onClick={saveUser} disabled={mode === 'none'}>저장</Button>
          <Popconfirm title="삭제하시겠습니까?" onConfirm={remove} okText="삭제" cancelText="취소" disabled={!isEdit}>
            <Button danger disabled={!isEdit}>삭제</Button>
          </Popconfirm>
        </Space>
      }
    >
      {mode === 'none' ? (
        <div style={{ color: '#999', padding: '24px 0', textAlign: 'center' }}>행을 선택하거나 [신규]를 누르세요.</div>
      ) : (
        <Form form={form} layout="vertical" initialValues={{ memCd: 'MEM01', statusCd: 'STATUS01' }}>
          <Form.Item name="userId" label="아이디" rules={[{ required: true, message: '아이디를 입력하세요.' }]}>
            <Input disabled={isEdit} />
          </Form.Item>

          {/* 신규: 비밀번호 필수 / 수정: 체크 시에만 새 비밀번호 입력 */}
          {!isEdit ? (
            <Form.Item name="userPw" label="비밀번호" rules={[{ required: true, message: '비밀번호를 입력하세요.' }]}>
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          ) : (
            <>
              <Form.Item name="changePw" valuePropName="checked">
                <Checkbox>비밀번호 변경</Checkbox>
              </Form.Item>
              {changePw && (
                <>
                  <Form.Item name="userPw" label="새 비밀번호" rules={[{ required: true, message: '새 비밀번호를 입력하세요.' }]}>
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item
                    name="userPwConfirm"
                    label="비밀번호 확인"
                    dependencies={['userPw']}
                    rules={[
                      { required: true, message: '비밀번호 확인을 입력하세요.' },
                      ({ getFieldValue }) => ({
                        validator: (_, v) =>
                          !v || getFieldValue('userPw') === v
                            ? Promise.resolve()
                            : Promise.reject(new Error('비밀번호가 일치하지 않습니다.')),
                      }),
                    ]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                </>
              )}
            </>
          )}

          <Form.Item name="userNm" label="이름" rules={[{ required: true, message: '이름을 입력하세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="memCd" label="회원유형">
            <CodeSelect pCodeId="MEM00" placeholder="회원유형 선택" />
          </Form.Item>
          <Form.Item name="statusCd" label="계정상태">
            <CodeSelect pCodeId="STATUS00" placeholder="계정상태 선택" />
          </Form.Item>
          <Form.Item name="genCd" label="성별">
            <CodeSelect pCodeId="GEN00" placeholder="성별 선택" allowClear />
          </Form.Item>
          <Form.Item name="birth" label="생년월일">
            <DateField allowClear placeholder="생년월일 선택" />
          </Form.Item>
          <Form.Item name="phone" label="연락처" rules={fieldRules('phone')}>
            <PhoneInput />
          </Form.Item>
          <Form.Item name="email" label="이메일" rules={fieldRules('email')}>
            <Input />
          </Form.Item>
        </Form>
      )}
    </Card>
  )

  return (
    <Card title="사용자 관리" styles={{ body: { padding: 12 } }}>
      <SplitLayout list={list} detail={detail} />
      <AuthgrpAssignModal open={authTarget !== null} user={authTarget} onClose={() => setAuthTarget(null)} />
    </Card>
  )
}
