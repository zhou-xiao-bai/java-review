import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  CheckCircle2,
  Circle,
  Loader2,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react'

import {
  admitTopicReviewUnits,
  createTopic,
  getApiErrorMessage,
  getTopicReviewUnits,
  getTopics,
  initializeTopicPoints,
  type ReviewUnitSummary,
  type ReviewUnitsResponse,
  type TopicSummary,
  updateTopicSelection,
  updateTopicSelections,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const topicsQueryKey = ['topics'] as const
const reviewUnitsQueryKey = ['review-units'] as const
const todayQueueQueryKey = ['today-queue'] as const

export function ScopePage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [activeDomainId, setActiveDomainId] = useState('all')
  const [selectedTopicId, setSelectedTopicId] = useState<string | null>(null)
  const [newTopicTitle, setNewTopicTitle] = useState('')
  const [newTopicDomainId, setNewTopicDomainId] = useState('')

  const topicsQuery = useQuery({
    queryKey: [...topicsQueryKey, search],
    queryFn: () => getTopics(search),
  })

  const topicsData = topicsQuery.data
  const domains = useMemo(() => topicsData?.domains ?? [], [topicsData])
  const visibleDomains =
    activeDomainId === 'all'
      ? domains
      : domains.filter((domain) => domain.id === activeDomainId)
  const visibleTopicIds = useMemo(
    () => visibleDomains.flatMap((domain) => domain.topics.map((topic) => topic.id)),
    [visibleDomains],
  )
  const visibleSelectedTopicIds = useMemo(
    () =>
      visibleDomains.flatMap((domain) =>
        domain.topics.filter((topic) => topic.selected).map((topic) => topic.id),
      ),
    [visibleDomains],
  )
  const visibleUnselectedTopicIds = useMemo(
    () =>
      visibleDomains.flatMap((domain) =>
        domain.topics.filter((topic) => !topic.selected).map((topic) => topic.id),
      ),
    [visibleDomains],
  )
  const allTopics = useMemo(
    () => domains.flatMap((domain) => domain.topics),
    [domains],
  )
  const selectedTopic =
    allTopics.find((topic) => topic.id === selectedTopicId) ??
    allTopics[0] ??
    null
  const selectedDomainId =
    newTopicDomainId || domains[0]?.id || ''

  const reviewUnitsQuery = useQuery({
    queryKey: [...reviewUnitsQueryKey, selectedTopic?.id],
    queryFn: () => getTopicReviewUnits(selectedTopic?.id ?? ''),
    enabled: Boolean(selectedTopic?.id),
  })

  const selectionMutation = useMutation({
    mutationFn: ({
      id,
      selected,
    }: {
      id: string
      selected: boolean
    }) => updateTopicSelection(id, selected),
    onSuccess: async (topic) => {
      setSelectedTopicId(topic.id)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: topicsQueryKey }),
        queryClient.invalidateQueries({ queryKey: reviewUnitsQueryKey }),
        queryClient.invalidateQueries({ queryKey: todayQueueQueryKey }),
      ])
    },
  })

  const bulkSelectionMutation = useMutation({
    mutationFn: ({
      topicIds,
      selected,
    }: {
      topicIds: string[]
      selected: boolean
    }) => updateTopicSelections(topicIds, selected),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: topicsQueryKey }),
        queryClient.invalidateQueries({ queryKey: reviewUnitsQueryKey }),
        queryClient.invalidateQueries({ queryKey: todayQueueQueryKey }),
      ])
    },
  })

  const initializeMutation = useMutation({
    mutationFn: initializeTopicPoints,
    onSuccess: async (topic) => {
      setSelectedTopicId(topic.id)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: topicsQueryKey }),
        queryClient.invalidateQueries({ queryKey: reviewUnitsQueryKey }),
        queryClient.invalidateQueries({ queryKey: todayQueueQueryKey }),
      ])
    },
  })

  const admitMutation = useMutation({
    mutationFn: ({
      topicId,
      reviewUnitIds,
    }: {
      topicId: string
      reviewUnitIds?: string[]
    }) => admitTopicReviewUnits(topicId, reviewUnitIds),
    onSuccess: async (data) => {
      setSelectedTopicId(data.topicId)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: topicsQueryKey }),
        queryClient.invalidateQueries({ queryKey: reviewUnitsQueryKey }),
        queryClient.invalidateQueries({ queryKey: todayQueueQueryKey }),
      ])
    },
  })

  const createMutation = useMutation({
    mutationFn: createTopic,
    onSuccess: async (topic) => {
      setSearch('')
      setNewTopicTitle('')
      setSelectedTopicId(topic.id)
      setActiveDomainId(topic.domainId)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: topicsQueryKey }),
        queryClient.invalidateQueries({ queryKey: reviewUnitsQueryKey }),
        queryClient.invalidateQueries({ queryKey: todayQueueQueryKey }),
      ])
    },
  })

  const errorMessage =
    getApiErrorMessage(selectionMutation.error, '') ||
    getApiErrorMessage(bulkSelectionMutation.error, '') ||
    getApiErrorMessage(initializeMutation.error, '') ||
    getApiErrorMessage(admitMutation.error, '') ||
    getApiErrorMessage(createMutation.error, '') ||
    (reviewUnitsQuery.isError ? getApiErrorMessage(reviewUnitsQuery.error) : '') ||
    (topicsQuery.isError ? getApiErrorMessage(topicsQuery.error) : '')

  function handleCreateTopic(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedDomainId) {
      return
    }
    createMutation.mutate({
      domainId: selectedDomainId,
      title: newTopicTitle,
    })
  }

  function handleBulkSelection(topicIds: string[], selected: boolean) {
    const uniqueTopicIds = [...new Set(topicIds)]
    if (uniqueTopicIds.length === 0) {
      return
    }
    bulkSelectionMutation.mutate({ topicIds: uniqueTopicIds, selected })
  }

  const selectionPending =
    selectionMutation.isPending || bulkSelectionMutation.isPending

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="text-sm font-medium text-emerald-700">M2 scope</div>
          <h1 className="mt-2 text-3xl font-semibold text-slate-950">
            学习范围
          </h1>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <Metric label="学习中主题" value={topicsQuery.data?.totals.selectedTopicCount ?? 0} />
          <Metric label="主题总数" value={topicsQuery.data?.totals.topicCount ?? 0} />
          <Metric label="复习单元" value={topicsQuery.data?.totals.reviewPointCount ?? 0} />
        </div>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_420px]">
        <div className="space-y-4">
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
              <label className="relative block">
                <Search
                  className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                  aria-hidden="true"
                />
                <input
                  className="h-10 w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 text-sm outline-none focus:border-slate-500"
                  placeholder="搜索主题或领域"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </label>

              <form
                className="grid gap-2 sm:grid-cols-[160px_minmax(180px,1fr)_auto]"
                onSubmit={handleCreateTopic}
              >
                <select
                  className="h-10 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                  value={selectedDomainId}
                  onChange={(event) => setNewTopicDomainId(event.target.value)}
                >
                  {domains.map((domain) => (
                    <option key={domain.id} value={domain.id}>
                      {domain.name}
                    </option>
                  ))}
                </select>
                <input
                  required
                  className="h-10 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                  maxLength={120}
                  placeholder="新增主题"
                  value={newTopicTitle}
                  onChange={(event) => setNewTopicTitle(event.target.value)}
                />
                <button
                  disabled={createMutation.isPending || !selectedDomainId}
                  type="submit"
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {createMutation.isPending ? (
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  ) : (
                    <Plus className="size-4" aria-hidden="true" />
                  )}
                  新增
                </button>
              </form>
            </div>

            <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
              <DomainFilterButton
                active={activeDomainId === 'all'}
                count={topicsQuery.data?.totals.selectedTopicCount ?? 0}
                label="全部"
                onClick={() => setActiveDomainId('all')}
              />
              {domains.map((domain) => (
                <DomainFilterButton
                  key={domain.id}
                  active={activeDomainId === domain.id}
                  count={domain.selectedCount}
                  label={domain.name}
                  onClick={() => setActiveDomainId(domain.id)}
                />
              ))}
            </div>

            <div className="mt-3 flex flex-wrap items-center gap-2">
              <BulkActionButton
                disabled={selectionPending || visibleUnselectedTopicIds.length === 0}
                loading={bulkSelectionMutation.isPending}
                label="全选可见"
                onClick={() => handleBulkSelection(visibleUnselectedTopicIds, true)}
              />
              <BulkActionButton
                disabled={selectionPending || visibleSelectedTopicIds.length === 0}
                loading={bulkSelectionMutation.isPending}
                label="清空可见"
                onClick={() => handleBulkSelection(visibleSelectedTopicIds, false)}
              />
              <span className="text-xs text-slate-500">
                当前可见 {visibleTopicIds.length} 个主题
              </span>
            </div>
          </div>

          {topicsQuery.isLoading ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              正在加载学习范围...
            </div>
          ) : visibleDomains.length === 0 ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              没有匹配的主题。
            </div>
          ) : (
            visibleDomains.map((domain) => (
              <div
                key={domain.id}
                className="rounded-lg border border-slate-200 bg-white shadow-sm"
              >
                <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
                  <div>
                    <h2 className="text-sm font-semibold text-slate-950">
                      {domain.name}
                    </h2>
                    <div className="mt-1 text-xs text-slate-500">
                      {domain.selectedCount}/{domain.topicCount} 学习中
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <BulkActionButton
                      disabled={
                        selectionPending ||
                        domain.topics.every((topic) => topic.selected)
                      }
                      loading={bulkSelectionMutation.isPending}
                      label="全选"
                      onClick={() =>
                        handleBulkSelection(
                          domain.topics
                            .filter((topic) => !topic.selected)
                            .map((topic) => topic.id),
                          true,
                        )
                      }
                    />
                    <BulkActionButton
                      disabled={
                        selectionPending ||
                        domain.topics.every((topic) => !topic.selected)
                      }
                      loading={bulkSelectionMutation.isPending}
                      label="清空"
                      onClick={() =>
                        handleBulkSelection(
                          domain.topics
                            .filter((topic) => topic.selected)
                            .map((topic) => topic.id),
                          false,
                        )
                      }
                    />
                  </div>
                </div>
                <div className="divide-y divide-slate-100">
                  {domain.topics.map((topic) => (
                    <TopicRow
                      key={topic.id}
                      disabled={selectionPending}
                      selected={selectedTopic?.id === topic.id}
                      topic={topic}
                      onPick={() => setSelectedTopicId(topic.id)}
                      onToggle={(selected) =>
                        selectionMutation.mutate({ id: topic.id, selected })
                      }
                    />
                  ))}
                </div>
              </div>
            ))
          )}
        </div>

        <TopicDetailPanel
          admitPending={admitMutation.isPending}
          initializePending={initializeMutation.isPending}
          reviewUnits={reviewUnitsQuery.data}
          reviewUnitsLoading={reviewUnitsQuery.isLoading}
          selectionPending={selectionPending}
          topic={selectedTopic}
          onAdmit={(topic, reviewUnitIds) =>
            admitMutation.mutate({ topicId: topic.id, reviewUnitIds })
          }
          onInitialize={(topic) => initializeMutation.mutate(topic.id)}
          onToggle={(topic, selected) =>
            selectionMutation.mutate({ id: topic.id, selected })
          }
        />
      </div>
    </section>
  )
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-28 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 text-xl font-semibold text-slate-950">{value}</div>
    </div>
  )
}

function DomainFilterButton({
  active,
  count,
  label,
  onClick,
}: {
  active: boolean
  count: number
  label: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'inline-flex h-9 shrink-0 items-center gap-2 rounded-md border px-3 text-sm font-medium transition',
        active
          ? 'border-slate-900 bg-slate-900 text-white'
          : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-950',
      )}
    >
      <span>{label}</span>
      <span
        className={cn(
          'rounded px-1.5 py-0.5 text-xs',
          active ? 'bg-white/15 text-white' : 'bg-slate-100 text-slate-500',
        )}
      >
        {count}
      </span>
    </button>
  )
}

function BulkActionButton({
  disabled,
  label,
  loading,
  onClick,
}: {
  disabled: boolean
  label: string
  loading: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="inline-flex h-8 items-center justify-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-600 hover:bg-slate-50 hover:text-slate-950 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading ? (
        <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
      ) : null}
      {label}
    </button>
  )
}

function TopicRow({
  disabled,
  selected,
  topic,
  onPick,
  onToggle,
}: {
  disabled: boolean
  selected: boolean
  topic: TopicSummary
  onPick: () => void
  onToggle: (selected: boolean) => void
}) {
  return (
    <div
      className={cn(
        'grid gap-3 px-4 py-3 transition md:grid-cols-[minmax(0,1fr)_140px_110px]',
        selected ? 'bg-emerald-50/60' : 'bg-white hover:bg-slate-50',
      )}
    >
      <div className="flex min-w-0 items-center gap-3">
        <input
          checked={topic.selected}
          className="size-4 rounded border-slate-300"
          disabled={disabled}
          type="checkbox"
          onChange={(event) => onToggle(event.target.checked)}
        />
        <button
          type="button"
          className="min-w-0 text-left"
          onClick={onPick}
        >
          <div className="flex min-w-0 items-center gap-2">
            {topic.selected ? (
              <CheckCircle2
                className="size-4 shrink-0 text-emerald-600"
                aria-hidden="true"
              />
            ) : (
              <Circle className="size-4 shrink-0 text-slate-300" aria-hidden="true" />
            )}
            <span className="truncate text-sm font-medium text-slate-950">
              {topic.title}
            </span>
            {topic.source === 'MANUAL' ? (
              <span className="rounded bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-700">
                手动
              </span>
            ) : null}
            <span
              className={cn(
                'rounded px-1.5 py-0.5 text-xs font-medium',
                topic.selected
                  ? 'bg-emerald-50 text-emerald-700'
                  : 'bg-slate-100 text-slate-500',
              )}
            >
              {tierLabel(topic.relevanceTier)}
            </span>
            <ScopeStateTags topic={topic} />
          </div>
          <div className="mt-1 truncate text-xs text-slate-500">
            {topic.weakPointSummary.length > 0
              ? topic.weakPointSummary.join(' / ')
              : scopeSummary(topic)}
          </div>
        </button>
      </div>

      <div className="flex items-center text-sm text-slate-600 md:justify-end">
        复习单元 {topic.reviewPointCount}
      </div>
      <div className="flex items-center text-sm font-medium text-slate-900 md:justify-end">
        {formatMastery(topic.averageMastery)}
      </div>
    </div>
  )
}

function ScopeStateTags({ topic }: { topic: TopicSummary }) {
  if (topic.reviewedReviewUnitCount > 0) {
    return (
      <span className="rounded bg-blue-50 px-1.5 py-0.5 text-xs font-medium text-blue-700">
        已首考 {topic.reviewedReviewUnitCount}
      </span>
    )
  }
  if (topic.pendingFirstReviewUnitCount > 0) {
    return (
      <span className="rounded bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-700">
        待首考 {topic.pendingFirstReviewUnitCount}
      </span>
    )
  }
  if (topic.admittedReviewUnitCount > 0) {
    return (
      <span className="rounded bg-emerald-50 px-1.5 py-0.5 text-xs font-medium text-emerald-700">
        已纳入 {topic.admittedReviewUnitCount}
      </span>
    )
  }
  return null
}

function scopeSummary(topic: TopicSummary) {
  if (topic.reviewedReviewUnitCount > 0 && !topic.selected) {
    return '已首考，复习记录保留'
  }
  if (topic.reviewedReviewUnitCount > 0) {
    return '已首考，按复习计划继续'
  }
  if (topic.pendingFirstReviewUnitCount > 0) {
    return '已纳入今日待首考'
  }
  if (topic.selected) {
    return '已标记为正在学'
  }
  return '未标记学习范围'
}

function TopicDetailPanel({
  admitPending,
  initializePending,
  reviewUnits,
  reviewUnitsLoading,
  selectionPending,
  topic,
  onAdmit,
  onInitialize,
  onToggle,
}: {
  admitPending: boolean
  initializePending: boolean
  reviewUnits: ReviewUnitsResponse | undefined
  reviewUnitsLoading: boolean
  selectionPending: boolean
  topic: TopicSummary | null
  onAdmit: (topic: TopicSummary, reviewUnitIds?: string[]) => void
  onInitialize: (topic: TopicSummary) => void
  onToggle: (topic: TopicSummary, selected: boolean) => void
}) {
  if (!topic) {
    return (
      <aside className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-500 shadow-sm">
        请选择一个主题。
      </aside>
    )
  }

  const units = reviewUnits?.units ?? []
  const unadmittedUnits = units.filter((unit) => !unit.stateId)

  return (
    <aside className="h-fit rounded-lg border border-slate-200 bg-white p-5 shadow-sm xl:sticky xl:top-24">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-xs font-medium text-slate-500">{topic.domainName}</div>
          <h2 className="mt-2 truncate text-xl font-semibold text-slate-950">
            {topic.title}
          </h2>
        </div>
        <span
          className={cn(
            'shrink-0 rounded px-2 py-1 text-xs font-medium',
            topic.selected
              ? 'bg-emerald-50 text-emerald-700'
              : 'bg-slate-100 text-slate-600',
          )}
        >
          {topic.selected ? '正在学' : '未标记'}
        </span>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3">
        <PanelMetric label="单元" value={String(reviewUnits?.totalCount ?? topic.reviewPointCount)} />
        <PanelMetric label="题目变体" value={String(reviewUnits?.questionVariantCount ?? 0)} />
        <PanelMetric label="已纳入" value={String(topic.admittedReviewUnitCount)} />
        <PanelMetric label="已首考" value={String(topic.reviewedReviewUnitCount)} />
      </div>
      {topic.pendingFirstReviewUnitCount > 0 ? (
        <div className="mt-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          {topic.pendingFirstReviewUnitCount} 个单元已进入待首考，会出现在今日复习中。
        </div>
      ) : null}
      {!topic.selected && topic.reviewedReviewUnitCount > 0 ? (
        <div className="mt-3 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-800">
          该主题已取消学习范围，但已首考记录会保留，并按复习计划继续复验。
        </div>
      ) : null}

      <div className="mt-5 space-y-3">
        <button
          disabled={selectionPending}
          type="button"
          onClick={() => onToggle(topic, !topic.selected)}
          className={cn(
            'inline-flex h-10 w-full items-center justify-center gap-2 rounded-md text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60',
            topic.selected
              ? 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
              : 'bg-slate-900 text-white hover:bg-slate-800',
          )}
        >
          {selectionPending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : topic.selected ? (
            <Circle className="size-4" aria-hidden="true" />
          ) : (
            <CheckCircle2 className="size-4" aria-hidden="true" />
          )}
          {topic.selected ? '不再标记正在学' : '标记为正在学'}
        </button>

        <button
          disabled={admitPending || reviewUnitsLoading || !topic.selected || unadmittedUnits.length === 0}
          type="button"
          onClick={() => onAdmit(topic)}
          className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-emerald-700 text-sm font-medium text-white hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {admitPending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Plus className="size-4" aria-hidden="true" />
          )}
          修复未纳入单元
        </button>

        <button
          disabled={initializePending}
          type="button"
          onClick={() => onInitialize(topic)}
          className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md border border-slate-200 bg-white text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {initializePending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <RefreshCw className="size-4" aria-hidden="true" />
          )}
          补齐复习单元
        </button>
      </div>

      <div className="mt-5 border-t border-slate-200 pt-5">
        <div className="flex items-center justify-between gap-3">
          <div className="text-sm font-semibold text-slate-950">复习单元</div>
          {reviewUnitsLoading ? (
            <Loader2 className="size-4 animate-spin text-slate-400" aria-hidden="true" />
          ) : (
            <span className="text-xs text-slate-500">
              {topic.admittedReviewUnitCount}/{reviewUnits?.totalCount ?? topic.reviewPointCount}
            </span>
          )}
        </div>

        {reviewUnitsLoading ? (
          <div className="mt-3 rounded-md bg-slate-50 px-3 py-4 text-sm text-slate-500">
            正在加载复习单元...
          </div>
        ) : units.length === 0 ? (
          <div className="mt-3 rounded-md bg-slate-50 px-3 py-4 text-sm text-slate-500">
            暂无复习单元
          </div>
        ) : (
          <div className="mt-3 max-h-[480px] space-y-2 overflow-y-auto pr-1">
            {units.map((unit) => (
              <ReviewUnitRow
                key={unit.reviewUnitId}
                admitPending={admitPending}
                topicSelected={topic.selected}
                unit={unit}
                onAdmit={() => onAdmit(topic, [unit.reviewUnitId])}
              />
            ))}
          </div>
        )}
      </div>

      <div className="mt-5 border-t border-slate-200 pt-5 text-sm">
        <div className="flex items-center justify-between gap-3">
          <span className="text-slate-500">下次复习</span>
          <span className="font-medium text-slate-900">
            {topic.nextReviewAt ? formatDate(topic.nextReviewAt) : '未安排'}
          </span>
        </div>
      </div>
    </aside>
  )
}

function ReviewUnitRow({
  admitPending,
  onAdmit,
  topicSelected,
  unit,
}: {
  admitPending: boolean
  onAdmit: () => void
  topicSelected: boolean
  unit: ReviewUnitSummary
}) {
  return (
    <div className="rounded-md border border-slate-200 px-3 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate text-sm font-medium text-slate-950">
            {unit.title}
          </div>
          <div className="mt-2 flex flex-wrap gap-1.5">
            <UnitStatusTag status={unit.stateStatus} />
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
              重要 {unit.importance}
            </span>
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
              难度 {unit.difficulty}
            </span>
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
              高频 {unit.interviewFrequency}
            </span>
            <span className="rounded bg-blue-50 px-1.5 py-0.5 text-xs font-medium text-blue-700">
              变体 {unit.questionVariantCount}
            </span>
          </div>
        </div>

        {!unit.stateId && topicSelected ? (
          <button
            type="button"
            disabled={admitPending}
            onClick={onAdmit}
            className="inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-md bg-slate-900 px-2.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {admitPending ? (
              <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
            ) : (
              <Plus className="size-3.5" aria-hidden="true" />
            )}
            纳入
          </button>
        ) : null}
      </div>

      <div className="mt-3 grid gap-2 text-xs text-slate-500 sm:grid-cols-2">
        <div>掌握度 {formatMastery(unit.mastery)}</div>
        <div>下次 {unit.nextReviewAt ? formatDate(unit.nextReviewAt) : '未安排'}</div>
      </div>
      {unit.weakPoints.length > 0 ? (
        <div className="mt-2 truncate text-xs text-rose-700">
          {unit.weakPoints.slice(0, 2).join(' / ')}
        </div>
      ) : null}
    </div>
  )
}

function UnitStatusTag({ status }: { status: ReviewUnitSummary['stateStatus'] }) {
  const label = reviewUnitStatusLabel(status)
  return (
    <span
      className={cn(
        'rounded px-1.5 py-0.5 text-xs font-medium',
        !status && 'bg-slate-100 text-slate-500',
        status === 'PENDING_FIRST_REVIEW' && 'bg-amber-50 text-amber-700',
        status === 'ACTIVE' && 'bg-emerald-50 text-emerald-700',
        status === 'ARCHIVED' && 'bg-slate-100 text-slate-500',
        status === 'NOT_FOR_ME' && 'bg-rose-50 text-rose-700',
      )}
    >
      {label}
    </span>
  )
}

function PanelMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-50 px-3 py-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 truncate text-sm font-semibold text-slate-950">{value}</div>
    </div>
  )
}

function formatMastery(value: number) {
  return `${Math.round((value / 5) * 100)}%`
}

function tierLabel(value: TopicSummary['relevanceTier']) {
  if (value === 'CORE') return '核心'
  if (value === 'PROJECT') return '项目'
  if (value === 'SUPPLEMENT') return '补充'
  if (value === 'ARCHIVED') return '归档'
  return value
}

function reviewUnitStatusLabel(status: ReviewUnitSummary['stateStatus']) {
  if (!status) return '未纳入'
  if (status === 'PENDING_FIRST_REVIEW') return '待首考'
  if (status === 'ACTIVE') return '复习中'
  if (status === 'ARCHIVED') return '已归档'
  if (status === 'NOT_FOR_ME') return '不适合'
  return status
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}
