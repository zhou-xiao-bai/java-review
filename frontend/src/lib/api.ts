export class ApiError extends Error {
  readonly status: number
  readonly details?: unknown

  constructor(message: string, status: number, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const headers = new Headers(options.headers)

  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include',
    body:
      options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  const contentType = response.headers.get('content-type')
  const payload = contentType?.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    throw new ApiError(
      `Request failed with status ${response.status}`,
      response.status,
      payload,
    )
  }

  return payload as T
}

export type HealthResponse = {
  status: 'UP' | string
  application: string
  checkedAt: string
}

export function getHealth() {
  return apiRequest<HealthResponse>('/api/health')
}

export type BootstrapStatusResponse = {
  initialized: boolean
}

export type CurrentUser = {
  id: string
  username: string
  email: string | null
  displayName: string
  role: string
}

export type BootstrapAdminRequest = {
  username: string
  email?: string
  password: string
  displayName: string
}

export type LoginRequest = {
  identifier: string
  password: string
  rememberMe: boolean
}

export function getBootstrapStatus() {
  return apiRequest<BootstrapStatusResponse>('/api/auth/bootstrap-status')
}

export function bootstrapAdmin(body: BootstrapAdminRequest) {
  return apiRequest<CurrentUser>('/api/auth/bootstrap-admin', {
    method: 'POST',
    body,
  })
}

export function login(body: LoginRequest) {
  return apiRequest<CurrentUser>('/api/auth/login', {
    method: 'POST',
    body,
  })
}

export function logout() {
  return apiRequest<void>('/api/auth/logout', {
    method: 'POST',
  })
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>('/api/auth/me')
}

export function getApiErrorMessage(
  error: unknown,
  fallback = 'Request failed.',
): string {
  if (error instanceof ApiError) {
    if (
      error.details &&
      typeof error.details === 'object' &&
      'message' in error.details &&
      typeof error.details.message === 'string'
    ) {
      return error.details.message
    }
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}
