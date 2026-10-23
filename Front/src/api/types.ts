export type ApiResponse<T> = {
  code: number
  message: string
  data: T
}

export type PageData<T> = {
  list: T[]
  page: number
  size: number
  total: number
}

export type Role = 'USER' | 'ADMIN'
