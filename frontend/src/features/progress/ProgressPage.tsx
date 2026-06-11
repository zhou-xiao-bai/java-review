import { Children, useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertCircle, CalendarDays, ChevronLeft, ChevronRight, Clock, Search } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import {
  getApiErrorMessage,
  getDueReviewPoints,
  getProgressDomains,
  getProgressOverview,
  getProgressTopics,
  getReviewPlanCalendar,
  getRecentReviewSessions,
  getWeakPoints,
  type DomainProgress,
  type ReviewPlanDay,
  type ReviewPlanItem,
  type TopicProgress,
  type WeakPointProgress,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const filters = [
  { label: '全部', value: '' },
  { label: '不稳定', value: 'unstable' },
  { label: '待复验', value: 'due' },
  { label: '稳定掌握', value: 'stable' },
  { label: '长期掌握', value: 'long_term' },
  { label: '未覆盖', value: 'uncovered' },
]

const progressTabs = [
  { value: 'plan', label: '按日期复习计划' },
  { value: 'domains', label: '领域状态分布' },
  { value: 'topics', label: '主题掌握列表' },
] as const

const planGroups = [
  { key: 'overdue', label: '逾期复验' },
  { key: 'due', label: '到期复验' },
  { key: 'pending_first_review', label: '待首考' },
  { key: 'other', label: '其他计划' },
] as const

const TOPIC_PAGE_SIZE = 20

type ProgressTab = (typeof progressTabs)[number]['value']
type PlanGroupKey = (typeof planGroups)[number]['key']

export function ProgressPage() {
  const [status, setStatus] = useState('')
  const [activeTab, setActiveTab] = useState<ProgressTab>('plan')
  const [calendarStartDate, setCalendarStartDate] = useState(() => localDateString())
  const [selectedCalendarDate, setSelectedCalendarDate] = useState(() => localDateString())
  const [topicSearch, setTopicSearch] = useState('')
  const [topicDomain, setTopicDomain] = useState('')
  const [visibleTopicCount, setVisibleTopicCount] = useState(TOPIC_PAGE_SIZE)
  const overviewQuery = useQuery({ queryKey: ['progress', 'overview'], queryFn: getProgressOverview })
  const domainsQuery = useQuery({ queryKey: ['progress', 'domains'], queryFn: getProgressDomains })
  const topicsQuery = useQuery({ queryKey: ['progress', 'topics', status], queryFn: () => getProgressTopics(status) })
  const weakPointsQuery = useQuery({ queryKey: ['progress', 'weak-points'], queryFn: getWeakPoints })
  const dueQuery = useQuery({ queryKey: ['progress', 'due'], queryFn: getDueReviewPoints })
  const recentQuery = useQuery({ queryKey: ['progress', 'recent'], queryFn: getRecentReviewSessions })
  const calendarQuery = useQuery({
    queryKey: ['progress', 'review-plan-calendar', calendarStartDate],
    queryFn: () => getReviewPlanCalendar(calendarStartDate, 14),
  })

  const errorMessage =
    (overviewQuery.isError ? getApiErrorMessage(overviewQuery.error) : '') ||
    (domainsQuery.isError ? getApiErrorMessage(domainsQuery.error) : '') ||
    (topicsQuery.isError ? getApiErrorMessage(topicsQuery.error) : '') ||
    (weakPointsQuery.isError ? getApiErrorMessage(weakPointsQuery.error) : '') ||
    (dueQuery.isError ? getApiErrorMessage(dueQuery.error) : '') ||
    (recentQuery.isError ? getApiErrorMessage(recentQuery.error) : '') ||
    (calendarQuery.isError ? getApiErrorMessage(calendarQuery.error) : '')

  const overview = overviewQuery.data
  const weaknessCategories = useMemo(
    () => aggregateWeaknessCategories(weakPointsQuery.data ?? []),
    [weakPointsQuery.data],
  )
  const topics = useMemo(() => topicsQuery.data ?? [], [topicsQuery.data])
  const topicDomainOptions = useMemo(
    () => [...new Set(topics.map((topic) => topic.domainName))].sort((left, right) => left.localeCompare(right, 'zh-CN')),
    [topics],
  )
  const filteredTopics = useMemo(
    () => filterTopics(topics, topicSearch, topicDomain),
    [topicDomain, topicSearch, topics],
  )
  const visibleTopics = filteredTopics.slice(0, visibleTopicCount)

  function resetTopicWindow() {
    setVisibleTopicCount(TOPIC_PAGE_SIZE)
  }

  function handleStatusChange(nextStatus: string) {
    setStatus(nextStatus)
    setTopicDomain('')
    resetTopicWindow()
  }

  function handleTopicSearchChange(value: string) {
    setTopicSearch(value)
    resetTopicWindow()
  }

  function handleTopicDomainChange(value: string) {
    setTopicDomain(value)
    resetTopicWindow()
  }

  return (
    <section className="space-y-5">
      <div>
        <h1 className="text-3xl font-semibold text-slate-950">复习进度</h1>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        <Metric label="总体掌握度" value={formatMastery(overview?.overallMastery ?? 0)} />
        <Metric label="自动主题" value={`${overview?.autoPlannableTopicCount ?? 0}/${overview?.selectedTopicCount ?? 0}`} />
        <Metric label="未稳定点" value={overview?.unstablePointCount ?? 0} />
        <Metric label="高风险点" value={overview?.highRiskPointCount ?? 0} />
        <Metric label="开放薄弱" value={overview?.openWeaknessCount ?? 0} />
        <Metric label="到期复习" value={overview?.dueReviewPointCount ?? 0} />
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <ProgressTabs activeTab={activeTab} onChange={setActiveTab} />

          {activeTab === 'plan' ? (
            <ReviewPlanCalendarPanel
              days={calendarQuery.data?.days ?? []}
              loading={calendarQuery.isLoading}
              selectedDate={selectedCalendarDate}
              onSelectedDateChange={setSelectedCalendarDate}
              onStartDateChange={setCalendarStartDate}
              startDate={calendarStartDate}
            />
          ) : null}

          {activeTab === 'domains' ? (
            <DomainStatusPanel domains={domainsQuery.data ?? []} loading={domainsQuery.isLoading} />
          ) : null}

          {activeTab === 'topics' ? (
            <TopicMasteryPanel
              domainOptions={topicDomainOptions}
              domainValue={topicDomain}
              loading={topicsQuery.isLoading}
              searchValue={topicSearch}
              status={status}
              topics={visibleTopics}
              totalCount={topics.length}
              filteredCount={filteredTopics.length}
              visibleCount={visibleTopics.length}
              onDomainChange={handleTopicDomainChange}
              onLoadMore={() => setVisibleTopicCount((count) => count + TOPIC_PAGE_SIZE)}
              onSearchChange={handleTopicSearchChange}
              onStatusChange={handleStatusChange}
            />
          ) : null}
        </div>

        <aside className="space-y-5">
          <Panel title="薄弱类型">
            {weaknessCategories.map((item) => (
              <ListItem key={item.category} title={categoryLabel(item.category)} meta={`${item.count} 个开放信号 / 最高严重度 ${item.maxSeverity}`} />
            ))}
          </Panel>
          <Panel title="薄弱点排行">
            {(weakPointsQuery.data ?? []).slice(0, 8).map((item) => (
              <ListItem key={`${item.pointTitle}-${item.weakPoint}`} title={item.weakPoint} meta={`${categoryLabel(item.category)} / 严重度 ${item.severity} / ${item.topicTitle}`} />
            ))}
          </Panel>
          <Panel title="待复验列表">
            {(dueQuery.data ?? []).slice(0, 8).map((item) => (
              <ListItem key={item.reviewPointId} title={item.pointTitle} meta={`${item.dueReason} / ${item.topicTitle} / ${formatDate(item.nextReviewAt)}`} />
            ))}
          </Panel>
          <Panel title="近期复习记录">
            {(recentQuery.data ?? []).slice(0, 8).map((item) => (
              <ListItem key={item.sessionId} title={item.pointTitle ?? '复习单元'} meta={`${statusLabel(item.status)} / ${item.finalScore ?? '-'} 分`} />
            ))}
          </Panel>
        </aside>
      </div>
    </section>
  )
}

function ProgressTabs({
  activeTab,
  onChange,
}: {
  activeTab: ProgressTab
  onChange: (tab: ProgressTab) => void
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
      <div className="grid gap-1 sm:grid-cols-3">
        {progressTabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => onChange(tab.value)}
            className={cn(
              'h-10 rounded-md px-3 text-sm font-medium transition-colors',
              activeTab === tab.value
                ? 'bg-slate-900 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950',
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>
    </div>
  )
}

function DomainStatusPanel({
  domains,
  loading,
}: {
  domains: DomainProgress[]
  loading: boolean
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-950">领域状态分布</h2>
      {loading ? (
        <div className="mt-4 rounded-md border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500">
          正在加载领域状态...
        </div>
      ) : (
        <>
          <div className="mt-4 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={domains}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="domainName" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="stablePointCount" stackId="status" fill="#059669" name="稳定" radius={[0, 0, 0, 0]} />
                <Bar dataKey="duePointCount" stackId="status" fill="#f59e0b" name="待复验" radius={[0, 0, 0, 0]} />
                <Bar dataKey="unstablePointCount" stackId="status" fill="#e11d48" name="不稳定" radius={[0, 0, 0, 0]} />
                <Bar dataKey="uncoveredPointCount" stackId="status" fill="#cbd5e1" name="未覆盖" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-3 grid gap-2 md:grid-cols-2">
            {domains.map((domain) => (
              <DomainStrip key={domain.domainId} domain={domain} />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

function TopicMasteryPanel({
  domainOptions,
  domainValue,
  filteredCount,
  loading,
  onDomainChange,
  onLoadMore,
  onSearchChange,
  onStatusChange,
  searchValue,
  status,
  topics,
  totalCount,
  visibleCount,
}: {
  domainOptions: string[]
  domainValue: string
  filteredCount: number
  loading: boolean
  onDomainChange: (value: string) => void
  onLoadMore: () => void
  onSearchChange: (value: string) => void
  onStatusChange: (status: string) => void
  searchValue: string
  status: string
  topics: TopicProgress[]
  totalCount: number
  visibleCount: number
}) {
  const hasMore = visibleCount < filteredCount

  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-5 py-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-base font-semibold text-slate-950">主题掌握列表</h2>
            <div className="mt-1 text-xs text-slate-500">
              显示 {visibleCount}/{filteredCount} 个匹配主题，当前状态 {totalCount} 个
            </div>
          </div>
          <div className="flex gap-2 overflow-x-auto pb-1">
            {filters.map((filter) => (
              <button
                key={filter.value}
                className={cn(
                  'h-8 shrink-0 rounded-md px-3 text-sm font-medium',
                  status === filter.value
                    ? 'bg-slate-900 text-white'
                    : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50',
                )}
                type="button"
                onClick={() => onStatusChange(filter.value)}
              >
                {filter.label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]">
          <label className="flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-600">
            <Search className="size-4 text-slate-400" aria-hidden="true" />
            <input
              value={searchValue}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="搜索主题、领域或薄弱点"
              className="min-w-0 flex-1 bg-transparent outline-none"
            />
          </label>
          <select
            value={domainValue}
            onChange={(event) => onDomainChange(event.target.value)}
            className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-slate-400"
          >
            <option value="">全部领域</option>
            {domainOptions.map((domain) => (
              <option key={domain} value={domain}>
                {domain}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading ? (
        <div className="p-5 text-sm text-slate-500">正在加载主题列表...</div>
      ) : (
        <>
          <div className="divide-y divide-slate-100">
            {topics.map((topic) => <TopicRow key={topic.topicId} topic={topic} />)}
            {filteredCount === 0 ? (
              <div className="p-5 text-sm text-slate-500">暂无匹配主题。</div>
            ) : null}
          </div>
          {hasMore ? (
            <div className="border-t border-slate-100 px-5 py-4">
              <button
                type="button"
                onClick={onLoadMore}
                className="inline-flex h-9 items-center rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                再显示 {Math.min(TOPIC_PAGE_SIZE, filteredCount - visibleCount)} 个
              </button>
            </div>
          ) : null}
        </>
      )}
    </section>
  )
}

function ReviewPlanCalendarPanel({
  days,
  loading,
  onSelectedDateChange,
  onStartDateChange,
  selectedDate,
  startDate,
}: {
  days: ReviewPlanDay[]
  loading: boolean
  onSelectedDateChange: (date: string) => void
  onStartDateChange: (date: string) => void
  selectedDate: string
  startDate: string
}) {
  const today = localDateString()
  const selectedDay = days.find((day) => day.date === selectedDate) ?? days[0]

  function shiftDays(offset: number) {
    const nextDate = addDaysString(startDate, offset)
    onStartDateChange(nextDate)
    onSelectedDateChange(nextDate)
  }

  function jumpToDate(date: string) {
    if (!date) return
    onStartDateChange(date)
    onSelectedDateChange(date)
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-2">
          <CalendarDays className="size-4 text-emerald-700" aria-hidden="true" />
          <h2 className="text-base font-semibold text-slate-950">按日期复习计划</h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            title="前 14 天"
            aria-label="前 14 天"
            onClick={() => shiftDays(-14)}
            className="inline-flex size-9 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-950"
          >
            <ChevronLeft className="size-4" aria-hidden="true" />
          </button>
          <span className="inline-flex h-9 items-center rounded-md border border-slate-200 bg-slate-50 px-3 text-sm font-medium text-slate-600">
            {formatDateWindow(startDate, days)}
          </span>
          <label className="inline-flex h-9 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700">
            <CalendarDays className="size-4 text-slate-400" aria-hidden="true" />
            <input
              type="date"
              value={selectedDate}
              onChange={(event) => jumpToDate(event.target.value)}
              className="bg-transparent text-sm outline-none"
            />
          </label>
          <button
            type="button"
            disabled={selectedDate === today}
            onClick={() => jumpToDate(today)}
            className="inline-flex h-9 items-center rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            今天
          </button>
          <button
            type="button"
            title="后 14 天"
            aria-label="后 14 天"
            onClick={() => shiftDays(14)}
            className="inline-flex size-9 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-950"
          >
            <ChevronRight className="size-4" aria-hidden="true" />
          </button>
        </div>
      </div>

      {loading ? (
        <div className="p-5 text-sm text-slate-500">正在加载复习计划...</div>
      ) : (
        <div>
          <div className="border-b border-slate-100 px-5 py-4">
            <div className="flex gap-2 overflow-x-auto pb-1">
              {days.map((day) => (
                <ReviewPlanDateButton
                  key={day.date}
                  day={day}
                  selected={day.date === selectedDay?.date}
                  today={today}
                  onSelect={() => onSelectedDateChange(day.date)}
                />
              ))}
            </div>
          </div>
          {selectedDay ? (
            <ReviewPlanSelectedDay day={selectedDay} />
          ) : (
            <div className="p-5 text-sm text-slate-500">暂无日期数据。</div>
          )}
        </div>
      )}
    </section>
  )
}

function ReviewPlanDateButton({
  day,
  onSelect,
  selected,
  today,
}: {
  day: ReviewPlanDay
  onSelect: () => void
  selected: boolean
  today: string
}) {
  const summary = summarizePlanItems(day.items)

  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        'min-h-[112px] w-[112px] shrink-0 rounded-lg border px-3 py-2 text-left transition-colors',
        selected
          ? 'border-slate-900 bg-slate-900 text-white shadow-sm'
          : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50',
      )}
    >
      <span
        className={cn(
          'text-xs font-medium',
          selected ? 'text-slate-300' : 'text-slate-500',
        )}
      >
        {formatWeekday(day.date)}
        {day.date === today ? ' · 今天' : ''}
      </span>
      <span className="mt-1 block text-lg font-semibold">
        {formatMonthDay(day.date)}
      </span>
      <span
        className={cn(
          'mt-1 block text-xs',
          selected ? 'text-slate-300' : 'text-slate-500',
        )}
      >
        {day.itemCount} 项 / {day.estimatedMinutes} 分
      </span>
      <span className="mt-2 flex flex-wrap gap-1">
        {summary.overdue > 0 ? (
          <DateMiniPill selected={selected} label={`逾 ${summary.overdue}`} />
        ) : null}
        {summary.due > 0 ? (
          <DateMiniPill selected={selected} label={`到 ${summary.due}`} />
        ) : null}
        {summary.pendingFirstReview > 0 ? (
          <DateMiniPill selected={selected} label={`首 ${summary.pendingFirstReview}`} />
        ) : null}
        {day.itemCount === 0 ? (
          <DateMiniPill selected={selected} label="空" />
        ) : null}
      </span>
    </button>
  )
}

function DateMiniPill({ label, selected }: { label: string; selected: boolean }) {
  return (
    <span
      className={cn(
        'rounded px-1.5 py-0.5 text-[11px] font-medium',
        selected ? 'bg-white/15 text-white' : 'bg-slate-100 text-slate-600',
      )}
    >
      {label}
    </span>
  )
}

function ReviewPlanSelectedDay({ day }: { day: ReviewPlanDay }) {
  const summary = summarizePlanItems(day.items)
  const groupedItems = groupPlanItems(day.items)

  return (
    <div className="px-5 py-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-base font-semibold text-slate-950">
            {formatPlanDate(day.date)}
          </div>
          <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
            <Clock className="size-3.5" aria-hidden="true" />
            {day.itemCount} 项 / {day.estimatedMinutes} 分钟
          </div>
        </div>
        <div className="flex flex-wrap gap-2 lg:justify-end">
          <PlanSummaryPill label="复习轨道" value={summary.total} />
          <PlanSummaryPill label="待首考" value={summary.pendingFirstReview} />
          <PlanSummaryPill label="逾期" value={summary.overdue} />
          <PlanSummaryPill label="到期" value={summary.due} />
        </div>
      </div>

      {day.items.length === 0 ? (
        <div className="mt-4 rounded-md border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500">
          暂无计划
        </div>
      ) : (
        <div className="mt-4 space-y-4">
          {groupedItems.map((group) => (
            <ReviewPlanItemGroup
              key={group.key}
              date={day.date}
              group={group}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function PlanSummaryPill({ label, value }: { label: string; value: number }) {
  return (
    <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
      {label} {value}
    </span>
  )
}

function ReviewPlanItemGroup({
  date,
  group,
}: {
  date: string
  group: { key: PlanGroupKey; label: string; items: ReviewPlanItem[] }
}) {
  const minutes = group.items.reduce((total, item) => total + item.estimatedMinutes, 0)

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span
          className={cn(
            'rounded px-2 py-1 text-xs font-semibold',
            planGroupTone(group.key),
          )}
        >
          {group.label}
        </span>
        <span className="text-xs text-slate-500">
          {group.items.length} 项 / {minutes} 分钟
        </span>
      </div>
      <div className="space-y-2">
        {group.items.map((item) => (
          <ReviewPlanItemRow
            key={item.reviewUnitStateId ?? `${date}-${item.reviewPointId}-${group.key}`}
            item={item}
          />
        ))}
      </div>
    </div>
  )
}

function ReviewPlanItemRow({ item }: { item: ReviewPlanItem }) {
  return (
    <div className="grid gap-3 rounded-md border border-slate-100 px-3 py-2 md:grid-cols-[minmax(0,1fr)_260px]">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-medium text-slate-950">
            {item.topicTitle ?? '手动加练'}
          </span>
          {item.domainName ? (
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">
              {item.domainName}
            </span>
          ) : null}
          <span
            className={cn(
              'rounded px-1.5 py-0.5 text-xs font-medium',
              item.source === 'review_unit_state'
                ? 'bg-emerald-50 text-emerald-700'
                : 'bg-amber-50 text-amber-700',
            )}
          >
            {item.source === 'review_unit_state' ? '复习轨道' : '计划项'}
          </span>
        </div>
        <div className="mt-1 line-clamp-2 text-sm leading-6 text-slate-600">
          {item.pointTitle}
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2 md:justify-end">
        <span
          className={cn(
            'rounded px-2 py-1 text-xs font-medium',
            item.type === 'due' && 'bg-rose-50 text-rose-700',
            item.type === 'pending_first_review' && 'bg-emerald-50 text-emerald-700',
          )}
        >
          {item.planReason}
        </span>
        <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
          {item.statusLabel}
        </span>
        <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
          {item.dueStatus}
        </span>
        <span className="text-xs text-slate-500">{item.estimatedMinutes} 分钟</span>
      </div>
    </div>
  )
}

function summarizePlanItems(items: ReviewPlanItem[]) {
  return {
    total: items.length,
    due: items.filter((item) => item.type === 'due' && item.planReason !== '逾期复验').length,
    pendingFirstReview: items.filter((item) => item.type === 'pending_first_review').length,
    overdue: items.filter((item) => item.planReason === '逾期复验').length,
  }
}

function groupPlanItems(items: ReviewPlanItem[]) {
  const grouped = new Map<PlanGroupKey, ReviewPlanItem[]>(
    planGroups.map((group) => [group.key, []]),
  )
  for (const item of items) {
    grouped.get(planGroupKey(item))?.push(item)
  }
  return planGroups
    .map((group) => ({
      key: group.key,
      label: group.label,
      items: grouped.get(group.key) ?? [],
    }))
    .filter((group) => group.items.length > 0)
}

function planGroupKey(item: ReviewPlanItem): PlanGroupKey {
  if (item.type === 'due' && item.planReason === '逾期复验') return 'overdue'
  if (item.type === 'due') return 'due'
  if (item.type === 'pending_first_review') return 'pending_first_review'
  return 'other'
}

function planGroupTone(key: PlanGroupKey) {
  if (key === 'overdue') return 'bg-rose-100 text-rose-800'
  if (key === 'due') return 'bg-rose-50 text-rose-700'
  if (key === 'pending_first_review') return 'bg-emerald-50 text-emerald-700'
  return 'bg-slate-100 text-slate-600'
}

function filterTopics(
  topics: TopicProgress[],
  searchValue: string,
  domainValue: string,
) {
  const keyword = normalizeSearchText(searchValue)
  return topics.filter((topic) => {
    if (domainValue && topic.domainName !== domainValue) {
      return false
    }
    if (!keyword) {
      return true
    }
    return normalizeSearchText([
      topic.topicTitle,
      topic.domainName,
      topic.weakPointSummary.join(' '),
    ].join(' ')).includes(keyword)
  })
}

function normalizeSearchText(value: string) {
  return value.trim().toLowerCase()
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-slate-950">{value}</div>
    </div>
  )
}

function TopicRow({ topic }: { topic: TopicProgress }) {
  return (
    <div className="grid gap-3 px-5 py-4 md:grid-cols-[minmax(0,1fr)_150px_160px_110px]">
      <div className="min-w-0">
        <div className="flex min-w-0 items-center gap-2">
          <span className="truncate text-sm font-medium text-slate-950">{topic.topicTitle}</span>
          <span className={cn('shrink-0 rounded px-1.5 py-0.5 text-xs font-medium', topic.planEnabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500')}>
            {tierLabel(topic.relevanceTier)}
          </span>
        </div>
        <div className="mt-1 truncate text-xs text-slate-500">{topic.weakPointSummary.join(' / ') || '暂无薄弱点'}</div>
      </div>
      <div className="text-sm text-slate-600">{topic.domainName}</div>
      <StatusStrip
        stable={topic.stablePointCount}
        due={topic.duePointCount}
        unstable={topic.unstablePointCount}
        uncovered={topic.uncoveredPointCount}
      />
      <div className="text-sm text-slate-600">
        {formatMastery(topic.averageMastery)}
        <div className="mt-1 text-xs text-slate-500">{formatDate(topic.nextReviewAt)}</div>
      </div>
    </div>
  )
}

function DomainStrip({ domain }: { domain: DomainProgress }) {
  return (
    <div className="rounded-md border border-slate-100 px-3 py-2">
      <div className="flex items-center justify-between gap-3 text-sm">
        <span className="font-medium text-slate-900">{domain.domainName}</span>
        <span className="text-xs text-slate-500">薄弱 {domain.openWeaknessCount}</span>
      </div>
      <div className="mt-2">
        <StatusStrip
          stable={domain.stablePointCount}
          due={domain.duePointCount}
          unstable={domain.unstablePointCount}
          uncovered={domain.uncoveredPointCount}
        />
      </div>
    </div>
  )
}

function StatusStrip({
  due,
  stable,
  uncovered,
  unstable,
}: {
  due: number
  stable: number
  uncovered: number
  unstable: number
}) {
  const total = Math.max(1, due + stable + uncovered + unstable)
  return (
    <div>
      <div className="flex h-2 overflow-hidden rounded-full bg-slate-100">
        <div className="bg-emerald-600" style={{ width: `${(stable / total) * 100}%` }} />
        <div className="bg-amber-500" style={{ width: `${(due / total) * 100}%` }} />
        <div className="bg-rose-600" style={{ width: `${(unstable / total) * 100}%` }} />
        <div className="bg-slate-300" style={{ width: `${(uncovered / total) * 100}%` }} />
      </div>
      <div className="mt-1 text-xs text-slate-500">
        稳 {stable} / 验 {due} / 弱 {unstable} / 新 {uncovered}
      </div>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  const hasChildren = Children.count(children) > 0

  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-950">{title}</div>
      <div className="divide-y divide-slate-100">
        {hasChildren ? children : <div className="p-4 text-sm text-slate-500">暂无数据</div>}
      </div>
    </section>
  )
}

function ListItem({ title, meta }: { title: string; meta: string }) {
  return (
    <div className="px-4 py-3">
      <div className="text-sm font-medium text-slate-950">{title}</div>
      <div className="mt-1 text-xs text-slate-500">{meta}</div>
    </div>
  )
}

function formatMastery(value: number) {
  return `${Math.round((value / 5) * 100)}%`
}

function formatDate(value: string | null) {
  if (!value) return '未安排'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value))
}

function formatDateWindow(startDate: string, days: ReviewPlanDay[]) {
  const endDate = days.length > 0 ? days[days.length - 1].date : addDaysString(startDate, 13)
  return `${formatMonthDay(startDate)} - ${formatMonthDay(endDate)}`
}

function formatPlanDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

function formatMonthDay(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(`${value}T00:00:00`))
}

function formatWeekday(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

function addDaysString(value: string, days: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return localDateString(date)
}

function localDateString(date = new Date()) {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return localDate.toISOString().slice(0, 10)
}

function aggregateWeaknessCategories(items: WeakPointProgress[]) {
  const map = new Map<string, { category: string; count: number; maxSeverity: number }>()
  for (const item of items) {
    const category = item.category || 'other'
    const current = map.get(category) ?? { category, count: 0, maxSeverity: 0 }
    current.count += 1
    current.maxSeverity = Math.max(current.maxSeverity, item.severity)
    map.set(category, current)
  }
  return [...map.values()].sort((left, right) => right.count - left.count || right.maxSeverity - left.maxSeverity)
}

function categoryLabel(value: string) {
  const labels: Record<string, string> = {
    concept_confusion: '概念混淆',
    expression_gap: '表达结构',
    insufficient_evidence: '证据不足',
    legacy: '历史记录',
    missing_boundary: '边界场景',
    missing_mechanism: '机制链路',
    missing_production: '生产排查',
    other: '其他',
    unknown: '不会',
  }
  return labels[value] ?? value
}

function tierLabel(value: string) {
  if (value === 'CORE') return '核心'
  if (value === 'PROJECT') return '项目'
  if (value === 'SUPPLEMENT') return '补充'
  if (value === 'ARCHIVED') return '归档'
  return value
}

function statusLabel(status: string) {
  if (status === 'evaluated') return '已收口'
  if (status === 'abandoned') return '已跳过'
  return '进行中'
}
