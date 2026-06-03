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

export type TopicSummary = {
  id: string
  domainId: string
  domainName: string
  code: string
  title: string
  source: 'BUILTIN' | 'MANUAL' | string
  selected: boolean
  reviewPointCount: number
  coveredReviewPointCount: number
  averageMastery: number
  nextReviewAt: string | null
  weakPointSummary: string[]
}

export type TopicDomain = {
  id: string
  code: string
  name: string
  topicCount: number
  selectedCount: number
  topics: TopicSummary[]
}

export type TopicTotals = {
  domainCount: number
  topicCount: number
  selectedTopicCount: number
  reviewPointCount: number
  averageMastery: number
}

export type TopicsResponse = {
  domains: TopicDomain[]
  totals: TopicTotals
}

export type CreateTopicRequest = {
  domainId: string
  title: string
}

export function getTopics(search?: string) {
  const params = new URLSearchParams()
  if (search?.trim()) {
    params.set('search', search.trim())
  }
  const query = params.toString()
  return apiRequest<TopicsResponse>(`/api/topics${query ? `?${query}` : ''}`)
}

export function createTopic(body: CreateTopicRequest) {
  return apiRequest<TopicSummary>('/api/topics', {
    method: 'POST',
    body,
  })
}

export function updateTopicSelection(id: string, selected: boolean) {
  return apiRequest<TopicSummary>(`/api/topics/${id}/selection`, {
    method: 'PATCH',
    body: { selected },
  })
}

export function initializeTopicPoints(id: string) {
  return apiRequest<TopicSummary>(`/api/topics/${id}/initialize-points`, {
    method: 'POST',
  })
}

export type SummaryMetric = {
  count: number
  minutes: number
}

export type TodaySummary = {
  carryOver: SummaryMetric
  due: SummaryMetric
  newExpansion: SummaryMetric
  manual: SummaryMetric
}

export type ReviewTask = {
  id: string
  reviewPointId: string | null
  topicId: string | null
  topicTitle: string | null
  domainName: string | null
  pointTitle: string | null
  manualPrompt: string | null
  date: string
  type: 'carry_over' | 'due' | 'new' | 'manual' | string
  typeLabel: string
  status: 'pending' | 'in_progress' | 'completed' | 'skipped' | string
  statusLabel: string
  priorityScore: number
  estimatedMinutes: number
  dueStatus: string
  nextReviewAt: string | null
  createdAt: string | null
  completedAt: string | null
}

export type ReviewTaskGroup = {
  type: ReviewTask['type']
  label: string
  count: number
  scheduledMinutes: number
  tasks: ReviewTask[]
}

export type TodayPlan = {
  date: string
  capacityMinutes: number
  scheduledMinutes: number
  completedMinutes: number
  remainingMinutes: number
  summary: TodaySummary
  groups: ReviewTaskGroup[]
}

export type CreateManualTaskRequest = {
  prompt: string
  estimatedMinutes?: number
}

export function getToday() {
  return apiRequest<TodayPlan>('/api/today')
}

export function generateToday() {
  return apiRequest<TodayPlan>('/api/today/generate', {
    method: 'POST',
  })
}

export function createManualTask(body: CreateManualTaskRequest) {
  return apiRequest<ReviewTask>('/api/today/manual-tasks', {
    method: 'POST',
    body,
  })
}

export function skipReviewTask(id: string) {
  return apiRequest<ReviewTask>(`/api/review-tasks/${id}/skip`, {
    method: 'PATCH',
  })
}

export type SettingsResponse = {
  llmProvider: string
  llmBaseUrl: string | null
  llmApiKeyMasked: string
  llmApiKeyConfigured: boolean
  llmModel: string
  activeLlmConfigId: string
  llmConfigs: LlmConfigResponse[]
  requestTimeoutSeconds: number
  dailyReviewMinutes: number
}

export type LlmConfigResponse = {
  id: string
  name: string
  provider: string
  baseUrl: string | null
  apiKeyMasked: string
  apiKeyConfigured: boolean
  model: string
}

export type UpdateSettingsRequest = {
  activeLlmConfigId: string
  llmConfigs: LlmConfigRequest[]
  requestTimeoutSeconds: number
  dailyReviewMinutes: number
}

export type LlmConfigRequest = {
  id: string
  name: string
  provider: string
  baseUrl: string
  apiKey?: string
  model: string
}

export type LlmTestResponse = {
  success: boolean
  message: string
  provider: string
  model: string
}

export function getSettings() {
  return apiRequest<SettingsResponse>('/api/settings')
}

export function updateSettings(body: UpdateSettingsRequest) {
  return apiRequest<SettingsResponse>('/api/settings', {
    method: 'PUT',
    body,
  })
}

export function testLlmSettings() {
  return apiRequest<LlmTestResponse>('/api/settings/llm/test', {
    method: 'POST',
  })
}

export type ReviewEvaluation = {
  overallComment: string
  correctPoints: string[]
  missingPoints: string[]
  inaccuratePoints: string[]
  referenceAnswer: string
  score: {
    conclusionAccuracy: number
    mechanismExplanation: number
    boundaryCases: number
    transferApplication: number
    overall: number
  }
  weakPoints: string[]
  nextProbe: string
  nextStatus: string
}

export type ReviewTurn = {
  id: string
  role: 'ai' | 'user' | 'system' | string
  turnType: string
  content: string
  createdAt: string | null
}

export type ReviewSession = {
  id: string
  taskId: string
  status: 'active' | 'evaluated' | 'abandoned' | string
  topicTitle: string | null
  pointTitle: string | null
  manualPrompt: string | null
  startedAt: string
  endedAt: string | null
  finalScore: number | null
  summary: string | null
  evaluation: ReviewEvaluation | null
  turns: ReviewTurn[]
}

export function startReviewSession(taskId: string) {
  return apiRequest<ReviewSession>('/api/review-sessions', {
    method: 'POST',
    body: { taskId },
  })
}

export function getReviewSession(id: string) {
  return apiRequest<ReviewSession>(`/api/review-sessions/${id}`)
}

export function answerReviewSession(id: string, answer: string) {
  return apiRequest<ReviewSession>(`/api/review-sessions/${id}/answer`, {
    method: 'POST',
    body: { answer },
  })
}

export function unknownReviewSession(id: string) {
  return apiRequest<ReviewSession>(`/api/review-sessions/${id}/unknown`, {
    method: 'POST',
  })
}

export function clarifyReviewSession(id: string, question?: string) {
  return apiRequest<ReviewSession>(`/api/review-sessions/${id}/clarify`, {
    method: 'POST',
    body: { question: question ?? '' },
  })
}

export function skipReviewSession(id: string) {
  return apiRequest<ReviewSession>(`/api/review-sessions/${id}/skip`, {
    method: 'POST',
  })
}

export type ProgressOverview = {
  overallMastery: number
  selectedTopicCount: number
  reviewPointCount: number
  unstablePointCount: number
  dueReviewPointCount: number
  completedSessionCount: number
}

export type DomainProgress = {
  domainId: string
  domainName: string
  topicCount: number
  reviewPointCount: number
  averageMastery: number
  unstablePointCount: number
}

export type TopicProgress = {
  topicId: string
  topicTitle: string
  domainName: string
  status: string
  reviewPointCount: number
  unstablePointCount: number
  averageMastery: number
  nextReviewAt: string | null
  weakPointSummary: string[]
}

export type WeakPointProgress = {
  weakPoint: string
  topicTitle: string
  pointTitle: string
  mastery: number
}

export type DueReviewPoint = {
  reviewPointId: string
  topicTitle: string
  pointTitle: string
  status: string
  mastery: number
  nextReviewAt: string | null
}

export type RecentReviewSession = {
  sessionId: string
  topicTitle: string | null
  pointTitle: string | null
  manualPrompt: string | null
  status: string
  finalScore: number | null
  startedAt: string
  endedAt: string | null
}

export function getProgressOverview() {
  return apiRequest<ProgressOverview>('/api/progress/overview')
}

export function getProgressDomains() {
  return apiRequest<DomainProgress[]>('/api/progress/domains')
}

export function getProgressTopics(status?: string) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  const query = params.toString()
  return apiRequest<TopicProgress[]>(`/api/progress/topics${query ? `?${query}` : ''}`)
}

export function getWeakPoints() {
  return apiRequest<WeakPointProgress[]>('/api/progress/weak-points')
}

export function getDueReviewPoints() {
  return apiRequest<DueReviewPoint[]>('/api/progress/due-review-points')
}

export function getRecentReviewSessions() {
  return apiRequest<RecentReviewSession[]>('/api/progress/recent-sessions')
}

export type ProjectCase = {
  id: string
  name: string
  background: string | null
  responsibility: string | null
  techStack: string[]
  highlights: string[]
  weakPoints: string[]
  createdAt: string | null
  updatedAt: string | null
}

export type ProjectCaseRequest = {
  name: string
  background: string
  responsibility: string
  techStack: string[]
  highlights: string[]
}

export type ProjectEvaluation = {
  overallComment: string
  score: {
    businessExpression: number
    technicalDesign: number
    tradeoffDecision: number
    metricsEvidence: number
    troubleshootingLoop: number
    interviewPressure: number
    overall: number
  }
  weakPoints: string[]
  suggestedTopics: string[]
}

export type ProjectTurn = {
  id: string
  role: string
  content: string
  createdAt: string | null
}

export type ProjectSession = {
  id: string
  projectCaseId: string
  status: string
  finalScore: number | null
  evaluation: ProjectEvaluation | null
  suggestedTopics: string[]
  turns: ProjectTurn[]
}

export function getProjectCases() {
  return apiRequest<ProjectCase[]>('/api/project-cases')
}

export function createProjectCase(body: ProjectCaseRequest) {
  return apiRequest<ProjectCase>('/api/project-cases', { method: 'POST', body })
}

export function updateProjectCase(id: string, body: ProjectCaseRequest) {
  return apiRequest<ProjectCase>(`/api/project-cases/${id}`, { method: 'PUT', body })
}

export function deleteProjectCase(id: string) {
  return apiRequest<void>(`/api/project-cases/${id}`, { method: 'DELETE' })
}

export function startProjectSession(projectCaseId: string) {
  return apiRequest<ProjectSession>(`/api/project-cases/${projectCaseId}/sessions`, { method: 'POST' })
}

export function answerProjectSession(sessionId: string, answer: string) {
  return apiRequest<ProjectSession>(`/api/project-sessions/${sessionId}/answer`, {
    method: 'POST',
    body: { answer },
  })
}
