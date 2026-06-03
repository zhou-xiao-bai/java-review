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
  createTopic,
  getApiErrorMessage,
  getTopics,
  initializeTopicPoints,
  type TopicSummary,
  updateTopicSelection,
  updateTopicSelections,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const topicsQueryKey = ['topics'] as const

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
      await queryClient.invalidateQueries({ queryKey: topicsQueryKey })
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
      await queryClient.invalidateQueries({ queryKey: topicsQueryKey })
    },
  })

  const initializeMutation = useMutation({
    mutationFn: initializeTopicPoints,
    onSuccess: async (topic) => {
      setSelectedTopicId(topic.id)
      await queryClient.invalidateQueries({ queryKey: topicsQueryKey })
    },
  })

  const createMutation = useMutation({
    mutationFn: createTopic,
    onSuccess: async (topic) => {
      setSearch('')
      setNewTopicTitle('')
      setSelectedTopicId(topic.id)
      setActiveDomainId(topic.domainId)
      await queryClient.invalidateQueries({ queryKey: topicsQueryKey })
    },
  })

  const errorMessage =
    getApiErrorMessage(selectionMutation.error, '') ||
    getApiErrorMessage(bulkSelectionMutation.error, '') ||
    getApiErrorMessage(initializeMutation.error, '') ||
    getApiErrorMessage(createMutation.error, '') ||
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
            范围管理
          </h1>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <Metric label="已选主题" value={topicsQuery.data?.totals.selectedTopicCount ?? 0} />
          <Metric label="主题总数" value={topicsQuery.data?.totals.topicCount ?? 0} />
          <Metric label="复习点" value={topicsQuery.data?.totals.reviewPointCount ?? 0} />
        </div>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
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
              正在加载主题范围...
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
                      {domain.selectedCount}/{domain.topicCount} 已选
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
          initializePending={initializeMutation.isPending}
          selectionPending={selectionPending}
          topic={selectedTopic}
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
          </div>
          <div className="mt-1 truncate text-xs text-slate-500">
            {topic.weakPointSummary.length > 0
              ? topic.weakPointSummary.join(' / ')
              : '暂无薄弱点记录'}
          </div>
        </button>
      </div>

      <div className="flex items-center text-sm text-slate-600 md:justify-end">
        复习点 {topic.coveredReviewPointCount}/{topic.reviewPointCount}
      </div>
      <div className="flex items-center text-sm font-medium text-slate-900 md:justify-end">
        {formatMastery(topic.averageMastery)}
      </div>
    </div>
  )
}

function TopicDetailPanel({
  initializePending,
  selectionPending,
  topic,
  onInitialize,
  onToggle,
}: {
  initializePending: boolean
  selectionPending: boolean
  topic: TopicSummary | null
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
          {topic.selected ? '已选' : '未选'}
        </span>
      </div>

      <div className="mt-5 grid grid-cols-3 gap-3">
        <PanelMetric label="掌握度" value={formatMastery(topic.averageMastery)} />
        <PanelMetric
          label="已覆盖"
          value={`${topic.coveredReviewPointCount}/${topic.reviewPointCount}`}
        />
        <PanelMetric label="来源" value={topic.source === 'MANUAL' ? '手动' : '内置'} />
      </div>

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
          {topic.selected ? '移出范围' : '加入范围'}
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
          补齐复习点
        </button>
        <div className="text-xs leading-5 text-slate-500">
          加入范围会自动准备复习点；此按钮用于补齐旧主题缺失的点。
        </div>
      </div>

      <div className="mt-5 border-t border-slate-200 pt-5">
        <div className="text-sm font-semibold text-slate-950">薄弱点摘要</div>
        {topic.weakPointSummary.length > 0 ? (
          <ul className="mt-3 space-y-2 text-sm text-slate-600">
            {topic.weakPointSummary.map((weakPoint) => (
              <li key={weakPoint} className="rounded-md bg-slate-50 px-3 py-2">
                {weakPoint}
              </li>
            ))}
          </ul>
        ) : (
          <div className="mt-3 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-500">
            暂无薄弱点记录
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}
