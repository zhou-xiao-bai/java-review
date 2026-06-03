import { useState, type ReactNode } from 'react'
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
  type TopicProgress,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const filters = [
  { label: '全部', value: '' },
  { label: '不稳定', value: 'unstable' },
  { label: '待复验', value: 'due' },
  { label: '稳定掌握', value: 'stable' },
  { label: '长期掌握', value: 'long_term' },
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

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <Metric label="总体掌握度" value={formatMastery(overview?.overallMastery ?? 0)} />
        <Metric label="已选主题" value={overview?.selectedTopicCount ?? 0} />
        <Metric label="未稳定点" value={overview?.unstablePointCount ?? 0} />
        <Metric label="到期复习" value={overview?.dueReviewPointCount ?? 0} />
        <Metric label="已完成会话" value={overview?.completedSessionCount ?? 0} />
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-semibold text-slate-950">领域掌握分布</h2>
            <div className="mt-4 h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={domainsQuery.data ?? []}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="domainName" tick={{ fontSize: 12 }} />
                  <YAxis domain={[0, 5]} tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Bar dataKey="averageMastery" fill="#059669" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
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
          <Panel title="薄弱点排行">
            {(weakPointsQuery.data ?? []).slice(0, 8).map((item) => (
              <ListItem key={`${item.pointTitle}-${item.weakPoint}`} title={item.weakPoint} meta={`${item.topicTitle} / ${item.pointTitle}`} />
            ))}
          </Panel>
          <Panel title="待复验列表">
            {(dueQuery.data ?? []).slice(0, 8).map((item) => (
              <ListItem key={item.reviewPointId} title={item.pointTitle} meta={`${item.topicTitle} / ${formatDate(item.nextReviewAt)}`} />
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
    <div className="grid gap-3 px-5 py-4 md:grid-cols-[minmax(0,1fr)_120px_120px_120px]">
      <div className="min-w-0">
        <div className="truncate text-sm font-medium text-slate-950">{topic.topicTitle}</div>
        <div className="mt-1 truncate text-xs text-slate-500">{topic.weakPointSummary.join(' / ') || '暂无薄弱点'}</div>
      </div>
      <div className="text-sm text-slate-600">{topic.domainName}</div>
      <div className="text-sm font-medium text-slate-900">{formatMastery(topic.averageMastery)}</div>
      <div className="text-sm text-slate-600">{formatDate(topic.nextReviewAt)}</div>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-950">{title}</div>
      <div className="divide-y divide-slate-100">{children || <div className="p-4 text-sm text-slate-500">暂无数据</div>}</div>
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

function statusLabel(status: string) {
  if (status === 'evaluated') return '已收口'
  if (status === 'abandoned') return '已跳过'
  return '进行中'
}
