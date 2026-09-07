import { createCrudApi } from '../../api/crudApi'

/** 접속 허용 IP VO */
export interface AccessIp {
  rowId?: string // PK(access_ip_id)
  ip?: string
  description?: string
  useYn?: string
}

export const accessIpApi = createCrudApi<AccessIp>('/adm/accessip', 'AccessIp')
export const ACCESSIP_LIST_URL = accessIpApi.listUrl
