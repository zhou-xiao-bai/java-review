import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  CalendarDays,
  CheckSquare,
  Clock,
  Loader2,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  SkipForward,
  Square,
  Trash2,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useCurrentUser } from '@/features/auth/hooks/useCurrentUser'
import {
  createManualTask,
  generateToday,
  getApiErrorMessage,
  getToday,
  regenerateToday,
  removeReviewTask,
  removeReviewTasks,
  skipReviewTask,
  unskipReviewTask,
  type ReviewTask,
  type ReviewTaskGroup,
  type SummaryMetric,
  type TodayPlan,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const todayQueryKey = ['today'] as const
const estimateOptions = [5, 10, 15, 20, 30]

export function TodayPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUserQuery = useCurrentUser()
  const [manualPrompt, setManualPrompt] = useState('')
  const [manualMinutes, setManualMinutes] = useState(10)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<string>>(
    () => new Set(),
  )

  const todayQuery = useQuery({
    queryKey: todayQueryKey,
    queryFn: getToday,
  })

  const generateMutation = useMutation({
    mutationFn: generateToday,
    onSuccess: (plan) => {
      applyTodayPlan(plan)
    },
  })

  const regenerateMutation = useMutation({
    mutationFn: regenerateToday,
    onSuccess: (plan) => {
      applyTodayPlan(plan)
    },
  })

  const manualMutation = useMutation({
    mutationFn: createManualTask,
    onSuccess: async () => {
      setManualPrompt('')
      setManualMinutes(10)
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })

  const skipMutation = useMutation({
    mutationFn: skipReviewTask,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })

  const unskipMutation = useMutation({
    mutationFn: unskipReviewTask,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })

  const removeMutation = useMutation({
    mutationFn: removeReviewTask,
    onSuccess: (plan) => {
      applyTodayPlan(plan)
    },
  })

  const batchRemoveMutation = useMutation({
    mutationFn: removeReviewTasks,
    onSuccess: (plan) => {
      applyTodayPlan(plan)
    },
  })

  const plan = todayQuery.data
  const allTasks = useMemo(
    () => plan?.groups.flatMap((group) => group.tasks) ?? [],
    [plan],
  )
  const allTaskIds = useMemo(() => allTasks.map((task) => task.id), [allTasks])
  const selectedVisibleTaskIds = useMemo(
    () => allTaskIds.filter((id) => selectedTaskIds.has(id)),
    [allTaskIds, selectedTaskIds],
  )
  const selectedCount = selectedVisibleTaskIds.length
  const allSelected = allTaskIds.length > 0 && selectedCount === allTaskIds.length
  const taskCount = useMemo(
    () => plan?.groups.reduce((count, group) => count + group.tasks.length, 0) ?? 0,
    [plan],
  )
  const pendingTaskCount = useMemo(
    () =>
      plan?.groups.reduce(
        (count, group) =>
          count +
          group.tasks.filter((task) =>
            ['pending', 'in_progress'].includes(task.status),
          ).length,
        0,
      ) ?? 0,
    [plan],
  )
  const errorMessage =
    getApiErrorMessage(generateMutation.error, '') ||
    getApiErrorMessage(regenerateMutation.error, '') ||
    getApiErrorMessage(manualMutation.error, '') ||
    getApiErrorMessage(skipMutation.error, '') ||
    getApiErrorMessage(unskipMutation.error, '') ||
    getApiErrorMessage(removeMutation.error, '') ||
    getApiErrorMessage(batchRemoveMutation.error, '') ||
    (todayQuery.isError ? getApiErrorMessage(todayQuery.error) : '')

  function applyTodayPlan(nextPlan: TodayPlan) {
    queryClient.setQueryData(todayQueryKey, nextPlan)
    const visibleIds = new Set(taskIds(nextPlan))
    setSelectedTaskIds((current) => {
      const next = new Set([...current].filter((id) => visibleIds.has(id)))
      return next.size === current.size ? current : next
    })
  }

  function handleManualSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    manualMutation.mutate({
      prompt: manualPrompt,
      estimatedMinutes: manualMinutes,
    })
  }

  function toggleAllTasks() {
    setSelectedTaskIds(() => (allSelected ? new Set() : new Set(allTaskIds)))
  }

  function toggleGroup(group: ReviewTaskGroup) {
    const groupIds = group.tasks.map((task) => task.id)
    const groupSelected = groupIds.every((id) => selectedTaskIds.has(id))
    setSelectedTaskIds((current) => {
      const next = new Set(current)
      for (const id of groupIds) {
        if (groupSelected) {
          next.delete(id)
        } else {
          next.add(id)
        }
      }
      return next
    })
  }

  function toggleTask(task: ReviewTask) {
    setSelectedTaskIds((current) => {
      const next = new Set(current)
      if (next.has(task.id)) {
        next.delete(task.id)
      } else {
        next.add(task.id)
      }
      return next
    })
  }

  function removeSelectedTasks() {
    const ids = selectedVisibleTaskIds
    if (ids.length > 0) {
      batchRemoveMutation.mutate(ids)
    }
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
            <CalendarDays className="size-4 text-emerald-700" aria-hidden="true" />
            <span>{formatPlanDate(plan?.date)}</span>
            {currentUserQuery.data ? (
              <span className="text-slate-300">/</span>
            ) : null}
            {currentUserQuery.data ? (
              <span>{currentUserQuery.data.displayName}</span>
            ) : null}
          </div>
          <h1 className="mt-2 text-3xl font-semibold text-slate-950">
            今日复习
          </h1>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={generateMutation.isPending || regenerateMutation.isPending}
            onClick={() => generateMutation.mutate()}
            className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {generateMutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <RefreshCw className="size-4" aria-hidden="true" />
            )}
            补齐今日计划
          </button>
          <button
            type="button"
            disabled={generateMutation.isPending || regenerateMutation.isPending}
            onClick={() => regenerateMutation.mutate()}
            className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {regenerateMutation.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <RefreshCw className="size-4" aria-hidden="true" />
            )}
            重排待开始任务
          </button>
          <button
            type="button"
            disabled={pendingTaskCount === 0}
            onClick={() => navigate('/review/session')}
            className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white shadow-sm hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Play className="size-4" aria-hidden="true" />
            手动选择
          </button>
          <button
            type="button"
            disabled={pendingTaskCount === 0}
            onClick={() => navigate('/review/session?mode=immersive')}
            className="inline-flex h-10 items-center gap-2 rounded-md bg-emerald-700 px-3 text-sm font-medium text-white shadow-sm hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Play className="size-4" aria-hidden="true" />
            沉浸式复习
          </button>
        </div>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-4">
          <PlanProgress plan={plan} loading={todayQuery.isLoading} />

          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SummaryCard
              label="顺延未完成"
              metric={plan?.summary.carryOver}
              tone="amber"
            />
            <SummaryCard label="今日到期" metric={plan?.summary.due} tone="rose" />
            <SummaryCard
              label="新拓展"
              metric={plan?.summary.newExpansion}
              tone="emerald"
            />
            <SummaryCard label="今日加练" metric={plan?.summary.manual} tone="slate" />
          </div>

          {taskCount > 0 ? (
            <SelectionToolbar
              allSelected={allSelected}
              pending={batchRemoveMutation.isPending}
              selectedCount={selectedCount}
              totalCount={allTaskIds.length}
              onClear={() => setSelectedTaskIds(new Set())}
              onRemove={removeSelectedTasks}
              onToggleAll={toggleAllTasks}
            />
          ) : null}

          {todayQuery.isLoading ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              正在加载今日计划...
            </div>
          ) : taskCount === 0 ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              暂无今日计划。请先在范围管理选择复习范围，再补齐今日计划，或追加一条今日加练。
            </div>
          ) : (
            plan?.groups.map((group) => (
              <TaskGroupPanel
                key={group.type}
                group={group}
                skippingTaskId={skipMutation.variables}
                skipPending={skipMutation.isPending}
                unskippingTaskId={unskipMutation.variables}
                unskipPending={unskipMutation.isPending}
                selectedTaskIds={selectedTaskIds}
                removingTaskId={removeMutation.variables}
                removePending={removeMutation.isPending}
                batchRemovingTaskIds={batchRemoveMutation.variables ?? []}
                batchRemovePending={batchRemoveMutation.isPending}
                onSkip={(task) => skipMutation.mutate(task.id)}
                onUnskip={(task) => unskipMutation.mutate(task.id)}
                onToggleGroup={() => toggleGroup(group)}
                onToggleTask={toggleTask}
                onRemove={(task) => removeMutation.mutate(task.id)}
              />
            ))
          )}
        </div>

        <aside className="h-fit rounded-lg border border-slate-200 bg-white p-5 shadow-sm lg:sticky lg:top-24">
          <div className="flex items-center gap-2">
            <Plus className="size-4 text-emerald-700" aria-hidden="true" />
            <h2 className="text-sm font-semibold text-slate-950">追加今日加练</h2>
          </div>

          <form className="mt-4 space-y-3" onSubmit={handleManualSubmit}>
            <textarea
              required
              rows={5}
              maxLength={1000}
              value={manualPrompt}
              onChange={(event) => setManualPrompt(event.target.value)}
              className="w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-slate-500"
              placeholder="例如：手写 AQS acquire 流程并说明中断处理边界"
            />

            <div className="grid grid-cols-[1fr_112px] gap-3">
              <label className="flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 text-sm text-slate-600">
                <Clock className="size-4 text-slate-400" aria-hidden="true" />
                预计时长
              </label>
              <select
                value={manualMinutes}
                onChange={(event) => setManualMinutes(Number(event.target.value))}
                className="h-10 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
              >
                {estimateOptions.map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {minutes} 分钟
                  </option>
                ))}
              </select>
            </div>

            <button
              type="submit"
              disabled={manualMutation.isPending}
              className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {manualMutation.isPending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Plus className="size-4" aria-hidden="true" />
              )}
              添加加练
            </button>
          </form>
        </aside>
      </div>
    </section>
  )
}

function PlanProgress({
  loading,
  plan,
}: {
  loading: boolean
  plan: TodayPlan | undefined
}) {
  const capacity = plan?.capacityMinutes ?? 60
  const scheduled = plan?.scheduledMinutes ?? 0
  const completed = plan?.completedMinutes ?? 0
  const scheduledPercent = percent(scheduled, capacity)
  const completedPercent = percent(completed, capacity)

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <div className="text-sm font-semibold text-slate-950">
            今日计划进度
          </div>
          <div className="mt-1 text-xs text-slate-500">
            {loading
              ? '加载中'
              : `已安排 ${scheduled}/${capacity} 分钟 · 已完成 ${completed} 分钟`}
          </div>
        </div>
        <div className="text-right">
          <div className="text-2xl font-semibold text-slate-950">
            {Math.min(scheduled, capacity)}
            <span className="text-sm font-medium text-slate-400">/{capacity}</span>
          </div>
          <div className="text-xs text-slate-500">分钟</div>
        </div>
      </div>

      <div className="relative mt-4 h-3 overflow-hidden rounded-full bg-slate-100">
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-slate-300"
          style={{ width: `${scheduledPercent}%` }}
        />
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-emerald-600"
          style={{ width: `${completedPercent}%` }}
        />
      </div>
    </div>
  )
}

function SummaryCard({
  label,
  metric,
  tone,
}: {
  label: string
  metric: SummaryMetric | undefined
  tone: 'amber' | 'emerald' | 'rose' | 'slate'
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div className="text-sm font-medium text-slate-500">{label}</div>
        <span
          className={cn(
            'size-2 rounded-full',
            tone === 'amber' && 'bg-amber-500',
            tone === 'emerald' && 'bg-emerald-600',
            tone === 'rose' && 'bg-rose-500',
            tone === 'slate' && 'bg-slate-500',
          )}
        />
      </div>
      <div className="mt-3 text-2xl font-semibold text-slate-950">
        {metric?.count ?? 0}
      </div>
      <div className="mt-1 text-xs text-slate-500">
        {metric?.minutes ?? 0} 分钟
      </div>
    </div>
  )
}

function SelectionToolbar({
  allSelected,
  onClear,
  onRemove,
  onToggleAll,
  pending,
  selectedCount,
  totalCount,
}: {
  allSelected: boolean
  onClear: () => void
  onRemove: () => void
  onToggleAll: () => void
  pending: boolean
  selectedCount: number
  totalCount: number
}) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm sm:flex-row sm:items-center sm:justify-between">
      <button
        type="button"
        onClick={onToggleAll}
        className="inline-flex h-9 w-fit items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
      >
        {allSelected ? (
          <CheckSquare className="size-4 text-emerald-700" aria-hidden="true" />
        ) : (
          <Square className="size-4" aria-hidden="true" />
        )}
        {allSelected ? '取消全选' : `全选 ${totalCount} 项`}
      </button>

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-slate-500">
          已选 {selectedCount} 项
        </span>
        {selectedCount > 0 ? (
          <button
            type="button"
            onClick={onClear}
            disabled={pending}
            className="inline-flex h-9 items-center rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
          >
            清空选择
          </button>
        ) : null}
        <button
          type="button"
          onClick={onRemove}
          disabled={selectedCount === 0 || pending}
          className="inline-flex h-9 items-center gap-2 rounded-md bg-rose-600 px-3 text-sm font-medium text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {pending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Trash2 className="size-4" aria-hidden="true" />
          )}
          移出今日计划
        </button>
      </div>
    </div>
  )
}

function TaskGroupPanel({
  batchRemovePending,
  batchRemovingTaskIds,
  group,
  onRemove,
  onSkip,
  onToggleGroup,
  onToggleTask,
  onUnskip,
  removePending,
  removingTaskId,
  selectedTaskIds,
  skipPending,
  skippingTaskId,
  unskipPending,
  unskippingTaskId,
}: {
  batchRemovePending: boolean
  batchRemovingTaskIds: string[]
  group: ReviewTaskGroup
  onRemove: (task: ReviewTask) => void
  onSkip: (task: ReviewTask) => void
  onToggleGroup: () => void
  onToggleTask: (task: ReviewTask) => void
  onUnskip: (task: ReviewTask) => void
  removePending: boolean
  removingTaskId: string | undefined
  selectedTaskIds: Set<string>
  skipPending: boolean
  skippingTaskId: string | undefined
  unskipPending: boolean
  unskippingTaskId: string | undefined
}) {
  if (group.tasks.length === 0) {
    return null
  }

  const groupSelected = group.tasks.every((task) => selectedTaskIds.has(task.id))
  const groupIndeterminate = !groupSelected && group.tasks.some((task) => selectedTaskIds.has(task.id))

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            title={groupSelected ? '取消选择本组' : '选择本组'}
            aria-label={groupSelected ? '取消选择本组' : '选择本组'}
            onClick={onToggleGroup}
            className={cn(
              'inline-flex size-8 shrink-0 items-center justify-center rounded-md border text-slate-500 hover:bg-slate-50 hover:text-slate-900',
              groupSelected || groupIndeterminate
                ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                : 'border-slate-200 bg-white',
            )}
          >
            {groupSelected || groupIndeterminate ? (
              <CheckSquare className="size-4" aria-hidden="true" />
            ) : (
              <Square className="size-4" aria-hidden="true" />
            )}
          </button>
          <div className="min-w-0">
            <h2 className="text-sm font-semibold text-slate-950">{group.label}</h2>
            <div className="mt-1 text-xs text-slate-500">
              {group.count} 项 · {group.scheduledMinutes} 分钟
            </div>
          </div>
        </div>
      </div>

      <div className="divide-y divide-slate-100">
        {group.tasks.map((task) => (
          <TaskRow
            key={task.id}
            task={task}
            selected={selectedTaskIds.has(task.id)}
            skipPending={skipPending && skippingTaskId === task.id}
            unskipPending={unskipPending && unskippingTaskId === task.id}
            removePending={
              (removePending && removingTaskId === task.id) ||
              (batchRemovePending && batchRemovingTaskIds.includes(task.id))
            }
            onRemove={() => onRemove(task)}
            onSkip={() => onSkip(task)}
            onToggle={() => onToggleTask(task)}
            onUnskip={() => onUnskip(task)}
          />
        ))}
      </div>
    </div>
  )
}

function TaskRow({
  onRemove,
  onSkip,
  onToggle,
  onUnskip,
  removePending,
  selected,
  skipPending,
  unskipPending,
  task,
}: {
  onRemove: () => void
  onSkip: () => void
  onToggle: () => void
  onUnskip: () => void
  removePending: boolean
  selected: boolean
  skipPending: boolean
  unskipPending: boolean
  task: ReviewTask
}) {
  const canSkip = ['pending', 'in_progress'].includes(task.status)
  const canUnskip = task.status === 'skipped'
  const actionPending = skipPending || unskipPending || removePending
  return (
    <div
      className={cn(
        'grid gap-3 px-4 py-3 lg:grid-cols-[32px_minmax(0,1fr)_170px_120px_92px]',
        selected && 'bg-emerald-50/50',
      )}
    >
      <div className="flex items-start pt-1">
        <button
          type="button"
          title={selected ? '取消选择' : '选择任务'}
          aria-label={selected ? '取消选择' : '选择任务'}
          onClick={onToggle}
          className={cn(
            'inline-flex size-8 items-center justify-center rounded-md border text-slate-500 hover:bg-slate-50 hover:text-slate-900',
            selected
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-slate-200 bg-white',
          )}
        >
          {selected ? (
            <CheckSquare className="size-4" aria-hidden="true" />
          ) : (
            <Square className="size-4" aria-hidden="true" />
          )}
        </button>
      </div>

      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-medium text-slate-950">
            {task.topicTitle ?? '手动加练'}
          </span>
          {task.domainName ? (
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">
              {task.domainName}
            </span>
          ) : null}
        </div>
        <div className="mt-1 line-clamp-2 text-sm leading-6 text-slate-600">
          {task.pointTitle ?? task.manualPrompt}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 lg:justify-end">
        <StatusTag status={task.status} label={task.statusLabel} />
        <PriorityTag score={task.priorityScore} />
        <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
          {task.dueStatus}
        </span>
      </div>

      <div className="flex items-center gap-2 text-sm text-slate-600 lg:justify-end">
        <Clock className="size-4 text-slate-400" aria-hidden="true" />
        {task.estimatedMinutes} 分钟
      </div>

      <div className="flex items-center gap-2 lg:justify-end">
        {canUnskip ? (
          <button
            type="button"
            title="取消跳过"
            aria-label="取消跳过"
            disabled={actionPending}
            onClick={onUnskip}
            className="inline-flex size-9 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {unskipPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <RotateCcw className="size-4" aria-hidden="true" />
            )}
          </button>
        ) : (
          <button
            type="button"
            title="跳过任务"
            aria-label="跳过任务"
            disabled={!canSkip || actionPending}
            onClick={onSkip}
            className="inline-flex size-9 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {skipPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <SkipForward className="size-4" aria-hidden="true" />
            )}
          </button>
        )}
        <button
          type="button"
          title="移出今日计划"
          aria-label="移出今日计划"
          disabled={actionPending}
          onClick={onRemove}
          className="inline-flex size-9 items-center justify-center rounded-md border border-rose-200 bg-white text-rose-500 hover:bg-rose-50 hover:text-rose-700 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {removePending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Trash2 className="size-4" aria-hidden="true" />
          )}
        </button>
      </div>
    </div>
  )
}

function StatusTag({ label, status }: { label: string; status: string }) {
  return (
    <span
      className={cn(
        'rounded px-2 py-1 text-xs font-medium',
        status === 'pending' && 'bg-slate-100 text-slate-600',
        status === 'in_progress' && 'bg-blue-50 text-blue-700',
        status === 'completed' && 'bg-emerald-50 text-emerald-700',
        status === 'skipped' && 'bg-zinc-100 text-zinc-500',
      )}
    >
      {label}
    </span>
  )
}

function PriorityTag({ score }: { score: number }) {
  const bucket =
    score >= 35 ? '高' : score >= 24 ? '中' : '低'
  return (
    <span
      className={cn(
        'rounded px-2 py-1 text-xs font-medium',
        bucket === '高' && 'bg-rose-50 text-rose-700',
        bucket === '中' && 'bg-amber-50 text-amber-700',
        bucket === '低' && 'bg-slate-100 text-slate-600',
      )}
    >
      优先级 {bucket}
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

function percent(value: number, capacity: number) {
  if (capacity <= 0) {
    return 0
  }
  return Math.min(100, Math.round((value / capacity) * 100))
}

function taskIds(plan: TodayPlan) {
  return plan.groups.flatMap((group) => group.tasks.map((task) => task.id))
}
