import { Children, useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertCircle } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import {
  getApiErrorMessage,
  getDueReviewPoints,
  getProgressDomains,
  getProgressOverview,
  getProgressTopics,
  getRecentReviewSessions,
  getWeakPoints,
  type DomainProgress,
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

export function ProgressPage() {
  const [status, setStatus] = useState('')
  const overviewQuery = useQuery({ queryKey: ['progress', 'overview'], queryFn: getProgressOverview })
  const domainsQuery = useQuery({ queryKey: ['progress', 'domains'], queryFn: getProgressDomains })
  const topicsQuery = useQuery({ queryKey: ['progress', 'topics', status], queryFn: () => getProgressTopics(status) })
  const weakPointsQuery = useQuery({ queryKey: ['progress', 'weak-points'], queryFn: getWeakPoints })
  const dueQuery = useQuery({ queryKey: ['progress', 'due'], queryFn: getDueReviewPoints })
  const recentQuery = useQuery({ queryKey: ['progress', 'recent'], queryFn: getRecentReviewSessions })

  const errorMessage =
    (overviewQuery.isError ? getApiErrorMessage(overviewQuery.error) : '') ||
    (domainsQuery.isError ? getApiErrorMessage(domainsQuery.error) : '') ||
    (topicsQuery.isError ? getApiErrorMessage(topicsQuery.error) : '') ||
    (weakPointsQuery.isError ? getApiErrorMessage(weakPointsQuery.error) : '') ||
    (dueQuery.isError ? getApiErrorMessage(dueQuery.error) : '') ||
    (recentQuery.isError ? getApiErrorMessage(recentQuery.error) : '')

  const overview = overviewQuery.data
  const weaknessCategories = useMemo(
    () => aggregateWeaknessCategories(weakPointsQuery.data ?? []),
    [weakPointsQuery.data],
  )

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
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-semibold text-slate-950">领域状态分布</h2>
            <div className="mt-4 h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={domainsQuery.data ?? []}>
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
              {(domainsQuery.data ?? []).map((domain) => (
                <DomainStrip key={domain.domainId} domain={domain} />
              ))}
            </div>
          </section>

          <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 md:flex-row md:items-center md:justify-between">
              <h2 className="text-base font-semibold text-slate-950">主题掌握列表</h2>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {filters.map((filter) => (
                  <button key={filter.value} className={cn('h-8 shrink-0 rounded-md px-3 text-sm font-medium', status === filter.value ? 'bg-slate-900 text-white' : 'border border-slate-200 bg-white text-slate-600')} type="button" onClick={() => setStatus(filter.value)}>
                    {filter.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="divide-y divide-slate-100">
              {(topicsQuery.data ?? []).map((topic) => <TopicRow key={topic.topicId} topic={topic} />)}
              {topicsQuery.data?.length === 0 ? <div className="p-5 text-sm text-slate-500">暂无匹配主题。</div> : null}
            </div>
          </section>
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
              <ListItem key={item.sessionId} title={item.pointTitle ?? item.manualPrompt ?? '今日加练'} meta={`${statusLabel(item.status)} / ${item.finalScore ?? '-'} 分`} />
            ))}
          </Panel>
        </aside>
      </div>
    </section>
  )
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
