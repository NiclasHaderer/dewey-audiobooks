export interface PaginatedResponse<T> {
  items: Array<T>
  limit: number
  offset: number
  total: number
}
