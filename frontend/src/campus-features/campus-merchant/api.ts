import { request, type PageResponse } from '../../campus-shared/http'

export interface MerchantSummary {
  id: string
  name: string
  category: string
  address: string
}

export function listMerchants(page = 1, size = 20, keyword = '') {
  const query = new URLSearchParams({ page: String(page), size: String(size), keyword })
  return request<PageResponse<MerchantSummary>>(`/merchants?${query}`)
}
