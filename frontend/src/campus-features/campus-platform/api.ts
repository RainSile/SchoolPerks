import { request } from '../../campus-shared/http'
export function getPlatform() {
  return request<{ stage: 'SCAFFOLD' }>('/platform')
}
