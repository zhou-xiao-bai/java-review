import { useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  BookOpenCheck,
  CalendarDays,
  CheckCircle2,
  Clock,
  EyeOff,
  Loader2,
  Play,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useCurrentUser } from '@/features/auth/hooks/useCurrentUser'
import {
  applyTodayAction,
  getApiErrorMessage,
  getTodayQueue,
  type TodayActionRequest,
  type TodayActionType,
  type TodayQueue,
  type TodayQueueGroup,
  type TodayQueueItem,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const todayQueueQueryKey = ['today-queue'] as const

export function TodayPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUserQuery = useCurrentUser()
  const queueQuery = useQuery({
    queryKey: todayQueueQueryKey,
    queryFn: getTodayQueue,
  })
  const actionMutation = useMutation({
    mutationFn: applyTodayAction,
    onSuccess: (queue) => {
      queryClient.setQueryData(todayQueueQueryKey, queue)
    },
  })
  const queue = queueQuery.data
  const items = useMemo(
    () => queue?.groups.flatMap((group) => group.items) ?? [],
    [queue],
  )
  const requiredCount = useMemo(
    () =>
      queue?.groups
        .filter((group) => ['overdue', 'due_today'].includes(group.reason))
        .reduce((count, group) => count + group.items.length, 0) ?? 0,
    [queue],
  )
  const firstReviewCount =
    queue?.groups.find((group) => group.reason === 'pending_first_review')?.items
      .length ?? 0
  const errorMessage = queueQuery.isError
    ? getApiErrorMessage(queueQuery.error)
    : getApiErrorMessage(actionMutation.error, '')
  const actionPending = actionMutation.isPending

  function applyQueueAction(
    item: TodayQueueItem,
    actionType: TodayActionType,
    postponeUntil?: string | null,
  ) {
    const body: TodayActionRequest = {
      reviewUnitStateId: item.stateId,
      actionType,
      postponeUntil,
    }
    actionMutation.mutate(body)
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
            <CalendarDays className="size-4 text-emerald-700" aria-hidden="true" />
            <span>{formatPlanDate(queue?.date)}</span>
            {currentUserQuery.data ? <span className="text-slate-300">/</span> : null}
            {currentUserQuery.data ? <span>{currentUserQuery.data.displayName}</span> : null}
          </div>
          <h1 className="mt-2 text-3xl font-semibold text-slate-950">
            今日复习队列
          </h1>
        </div>

        <button
          type="button"
          disabled={items.length === 0}
          onClick={() => navigate('/review/session?mode=immersive')}
          className="inline-flex h-10 w-fit items-center gap-2 rounded-md bg-emerald-700 px-3 text-sm font-medium text-white shadow-sm hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Play className="size-4" aria-hidden="true" />
          沉浸式复习
        </button>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <TodayQueuePanel
        firstReviewCount={firstReviewCount}
        loading={queueQuery.isLoading}
        queue={queue}
        requiredCount={requiredCount}
        totalCount={items.length}
        actionPending={actionPending}
        pendingActionStateId={actionPending ? actionMutation.variables?.reviewUnitStateId ?? null : null}
        onAction={applyQueueAction}
        onStart={(item) => navigate(`/review/session?stateId=${item.stateId}`)}
      />
    </section>
  )
}

function TodayQueuePanel({
  firstReviewCount,
  loading,
  onAction,
  onStart,
  actionPending,
  pendingActionStateId,
  queue,
  requiredCount,
  totalCount,
}: {
  firstReviewCount: number
  loading: boolean
  onAction: (item: TodayQueueItem, actionType: TodayActionType, postponeUntil?: string | null) => void
  onStart: (item: TodayQueueItem) => void
  actionPending: boolean
  pendingActionStateId: string | null
  queue: TodayQueue | undefined
  requiredCount: number
  totalCount: number
}) {
  const groups = queue?.groups ?? []
  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 md:flex-row md:items-center md:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <BookOpenCheck className="size-4 text-emerald-700" aria-hidden="true" />
            <h2 className="text-sm font-semibold text-slate-950">
              动态复习队列
            </h2>
          </div>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            今日队列只查询复习单元状态，不创建、不补齐、不恢复任务。
          </p>
        </div>

        <div className="grid grid-cols-3 gap-2 text-center">
          <QueueMetric label="全部" value={totalCount} />
          <QueueMetric label="必须处理" value={requiredCount} tone="rose" />
          <QueueMetric label="待首考" value={firstReviewCount} tone="emerald" />
        </div>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 px-5 py-8 text-sm text-slate-500">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          正在加载复习队列...
        </div>
      ) : totalCount === 0 ? (
        <div className="px-5 py-8 text-sm leading-6 text-slate-500">
          暂无需要处理的复习单元。已准入但未到期的内容会在对应日期出现。
        </div>
      ) : (
        <div className="divide-y divide-slate-100">
          {groups.map((group) => (
            <TodayQueueGroupPanel
              key={group.reason}
              group={group}
              onAction={onAction}
              onStart={onStart}
              actionPending={actionPending}
              pendingActionStateId={pendingActionStateId}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function QueueMetric({
  label,
  tone = 'slate',
  value,
}: {
  label: string
  tone?: 'emerald' | 'rose' | 'slate'
  value: number
}) {
  return (
    <div className="min-w-20 rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
      <div
        className={cn(
          'text-lg font-semibold',
          tone === 'emerald' && 'text-emerald-700',
          tone === 'rose' && 'text-rose-700',
          tone === 'slate' && 'text-slate-950',
        )}
      >
        {value}
      </div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  )
}

function TodayQueueGroupPanel({
  group,
  onAction,
  onStart,
  actionPending,
  pendingActionStateId,
}: {
  group: TodayQueueGroup
  onAction: (item: TodayQueueItem, actionType: TodayActionType, postponeUntil?: string | null) => void
  onStart: (item: TodayQueueItem) => void
  actionPending: boolean
  pendingActionStateId: string | null
}) {
  if (group.items.length === 0) {
    return null
  }

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-2 bg-slate-50 px-5 py-3">
        <div className="flex items-center gap-2">
          <QueueReasonDot reason={group.reason} />
          <h3 className="text-sm font-semibold text-slate-950">{group.label}</h3>
        </div>
        <span className="text-xs font-medium text-slate-500">
          {group.items.length} 个复习单元
        </span>
      </div>

      <div className="divide-y divide-slate-100">
        {group.items.map((item) => (
          <TodayQueueRow
            key={item.reviewUnitId}
            item={item}
            pending={pendingActionStateId === item.stateId}
            actionPending={actionPending}
            onAction={(actionType, postponeUntil) => onAction(item, actionType, postponeUntil)}
            onStart={() => onStart(item)}
          />
        ))}
      </div>
    </section>
  )
}

function TodayQueueRow({
  item,
  onAction,
  onStart,
  actionPending,
  pending,
}: {
  item: TodayQueueItem
  onAction: (actionType: TodayActionType, postponeUntil?: string | null) => void
  onStart: () => void
  actionPending: boolean
  pending: boolean
}) {
  const disabled = pending || actionPending
  return (
    <div className="grid gap-3 px-5 py-4 xl:grid-cols-[minmax(0,1fr)_330px_278px]">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold text-slate-950">
            {item.scopeTitle}
          </span>
          <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">
            {item.domainName}
          </span>
          <QueueStatusTag status={item.status} />
        </div>
        <div className="mt-1 line-clamp-2 text-sm leading-6 text-slate-600">
          {item.unitTitle}
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
          <span>准入 {formatInstantDate(item.admittedAt)}</span>
          <span>上次 {formatInstantDate(item.lastReviewedAt)}</span>
          <span>下次 {formatInstantDate(item.nextReviewAt)}</span>
        </div>
      </div>

      <div className="flex flex-wrap items-start gap-2 xl:justify-end">
        <QueueReasonTag item={item} />
        <ReviewFactorTag label="重要度" value={item.importance} tone="rose" />
        <ReviewFactorTag label="难度" value={item.difficulty} tone="amber" />
        <ReviewFactorTag
          label="频率"
          value={item.interviewFrequency}
          tone="emerald"
        />
        <AttemptHistoryTag item={item} />
      </div>

      <div className="flex flex-wrap items-center gap-2 xl:justify-end">
        <button
          type="button"
          title="今天先不做，只从今日队列隐藏"
          disabled={disabled}
          onClick={() => onAction('DISMISS_TODAY')}
          className="inline-flex h-9 items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {pending ? (
            <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
          ) : (
            <EyeOff className="size-3.5" aria-hidden="true" />
          )}
          今日不做
        </button>
        <button
          type="button"
          title="推迟到明天复习，会更新长期排期"
          disabled={disabled}
          onClick={() => onAction('POSTPONE')}
          className="inline-flex h-9 items-center gap-1.5 rounded-md border border-amber-200 bg-amber-50 px-2.5 text-xs font-medium text-amber-800 hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {pending ? (
            <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
          ) : (
            <Clock className="size-3.5" aria-hidden="true" />
          )}
          明天再看
        </button>
        <button
          type="button"
          title="记录一次自评掌握，并进入长期复验"
          disabled={disabled}
          onClick={() => onAction('SELF_MASTERED')}
          className="inline-flex h-9 items-center gap-1.5 rounded-md border border-emerald-200 bg-emerald-50 px-2.5 text-xs font-medium text-emerald-800 hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {pending ? (
            <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
          ) : (
            <CheckCircle2 className="size-3.5" aria-hidden="true" />
          )}
          已掌握
        </button>
        <button
          type="button"
          onClick={onStart}
          disabled={disabled}
          className="inline-flex h-9 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800"
        >
          <Play className="size-4" aria-hidden="true" />
          开始
        </button>
      </div>
    </div>
  )
}

function QueueReasonDot({ reason }: { reason: string }) {
  return (
    <span
      className={cn(
        'size-2 rounded-full',
        reason === 'overdue' && 'bg-rose-500',
        reason === 'due_today' && 'bg-amber-500',
        reason === 'manual_add' && 'bg-blue-500',
        reason === 'pending_first_review' && 'bg-emerald-600',
      )}
    />
  )
}

function QueueReasonTag({ item }: { item: TodayQueueItem }) {
  return (
    <span
      className={cn(
        'rounded px-2 py-1 text-xs font-medium',
        item.reason === 'overdue' && 'bg-rose-50 text-rose-700',
        item.reason === 'due_today' && 'bg-amber-50 text-amber-700',
        item.reason === 'manual_add' && 'bg-blue-50 text-blue-700',
        item.reason === 'pending_first_review' && 'bg-emerald-50 text-emerald-700',
      )}
    >
      {item.reasonLabel}
    </span>
  )
}

function QueueStatusTag({ status }: { status: string }) {
  const label =
    status === 'PENDING_FIRST_REVIEW'
      ? '待首考'
      : status === 'ACTIVE'
        ? '复习中'
        : status
  return (
    <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs font-medium text-zinc-600">
      {label}
    </span>
  )
}

function ReviewFactorTag({
  label,
  tone,
  value,
}: {
  label: string
  tone: 'amber' | 'emerald' | 'rose'
  value: number
}) {
  return (
    <span
      className={cn(
        'rounded px-2 py-1 text-xs font-medium',
        tone === 'amber' && 'bg-amber-50 text-amber-700',
        tone === 'emerald' && 'bg-emerald-50 text-emerald-700',
        tone === 'rose' && 'bg-rose-50 text-rose-700',
      )}
    >
      {label} {value}
    </span>
  )
}

function AttemptHistoryTag({ item }: { item: TodayQueueItem }) {
  const resultLabel = reviewResultLabel(item.lastResult)
  if (!resultLabel && item.consecutiveSuccessCount === 0 && item.consecutiveFailureCount === 0) {
    return (
      <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
        暂无记录
      </span>
    )
  }

  return (
    <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
      {resultLabel ? `上次${resultLabel}` : '有记录'} · 连对 {item.consecutiveSuccessCount} · 连错 {item.consecutiveFailureCount}
    </span>
  )
}

function formatPlanDate(value: string | undefined) {
  if (!value) {
    return '今日'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

function formatInstantDate(value: string | null) {
  if (!value) {
    return '无'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}

function reviewResultLabel(value: string | null) {
  if (!value) {
    return ''
  }
  const labels: Record<string, string> = {
    GOOD: '好',
    PARTIAL: '一般',
    POOR: '差',
    SELF_MASTERED: '自评掌握',
  }
  return labels[value] ?? value
}
