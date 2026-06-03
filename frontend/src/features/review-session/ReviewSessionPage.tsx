import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, HelpCircle, Loader2, Play, Send, SkipForward } from 'lucide-react'

import {
  answerReviewSession,
  clarifyReviewSession,
  generateToday,
  getApiErrorMessage,
  getToday,
  skipReviewSession,
  startReviewSession,
  unknownReviewSession,
  type ReviewSession,
  type ReviewTask,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const todayQueryKey = ['today'] as const

export function ReviewSessionPage() {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<ReviewSession | null>(null)
  const [answer, setAnswer] = useState('')
  const [clarifyText, setClarifyText] = useState('')
  const todayQuery = useQuery({ queryKey: todayQueryKey, queryFn: getToday })
  const tasks = useMemo(
    () => todayQuery.data?.groups.flatMap((group) => group.tasks) ?? [],
    [todayQuery.data],
  )
  const activeTasks = tasks.filter((task) => ['pending', 'in_progress'].includes(task.status))

  const generateMutation = useMutation({
    mutationFn: generateToday,
    onSuccess: (plan) => queryClient.setQueryData(todayQueryKey, plan),
  })
  const startMutation = useMutation({
    mutationFn: startReviewSession,
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })
  const answerMutation = useMutation({
    mutationFn: ({ id, value }: { id: string; value: string }) => answerReviewSession(id, value),
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      setAnswer('')
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })
  const unknownMutation = useMutation({ mutationFn: unknownReviewSession, onSuccess: setSession })
  const clarifyMutation = useMutation({
    mutationFn: ({ id, question }: { id: string; question: string }) => clarifyReviewSession(id, question),
    onSuccess: (nextSession) => {
      setSession(nextSession)
      setClarifyText('')
    },
  })
  const skipMutation = useMutation({
    mutationFn: skipReviewSession,
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })

  const errorMessage =
    getApiErrorMessage(generateMutation.error, '') ||
    getApiErrorMessage(startMutation.error, '') ||
    getApiErrorMessage(answerMutation.error, '') ||
    getApiErrorMessage(unknownMutation.error, '') ||
    getApiErrorMessage(clarifyMutation.error, '') ||
    getApiErrorMessage(skipMutation.error, '') ||
    (todayQuery.isError ? getApiErrorMessage(todayQuery.error) : '')

  function submitAnswer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    answerMutation.mutate({ id: session.id, value: answer })
  }

  function startNextTask() {
    const next = activeTasks.find((task) => task.id !== session?.taskId) ?? activeTasks[0]
    if (next) startMutation.mutate(next.id)
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-3xl font-semibold text-slate-950">复习会话</h1>
        </div>
        <button
          className="inline-flex h-10 w-fit items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          disabled={generateMutation.isPending}
          type="button"
          onClick={() => generateMutation.mutate()}
        >
          {generateMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
          生成今日队列
        </button>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
        <aside className="h-fit rounded-lg border border-slate-200 bg-white shadow-sm xl:sticky xl:top-24">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-950">今日队列</h2>
            <div className="mt-1 text-xs text-slate-500">{activeTasks.length} 个待复习任务</div>
          </div>
          <div className="max-h-[620px] divide-y divide-slate-100 overflow-auto">
            {tasks.length === 0 ? (
              <div className="p-4 text-sm text-slate-500">暂无任务。</div>
            ) : (
              tasks.map((task) => (
                <TaskButton
                  key={task.id}
                  active={session?.taskId === task.id}
                  disabled={startMutation.isPending || !['pending', 'in_progress'].includes(task.status)}
                  task={task}
                  onStart={() => startMutation.mutate(task.id)}
                />
              ))
            )}
          </div>
        </aside>

        <main className="space-y-4">
          {!session ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              请选择一个今日任务开始严格面试复习。
            </div>
          ) : (
            <>
              <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div>
                    <div className="text-xs font-medium text-slate-500">{session.topicTitle ?? '今日加练'}</div>
                    <h2 className="mt-1 text-xl font-semibold text-slate-950">
                      {session.pointTitle ?? session.manualPrompt}
                    </h2>
                  </div>
                  <StatusTag status={session.status} />
                </div>
              </section>

              <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-950">
                  对话记录
                </div>
                <div className="space-y-3 p-5">
                  {session.turns.map((turn) => (
                    <div key={turn.id} className={cn('rounded-md px-3 py-2 text-sm', turn.role === 'user' ? 'bg-slate-100 text-slate-900' : 'bg-emerald-50 text-emerald-900')}>
                      <div className="mb-1 text-xs font-medium opacity-70">{turn.role === 'user' ? '我' : '面试官'} / {turn.turnType}</div>
                      <div className="whitespace-pre-wrap leading-6">{turn.content}</div>
                    </div>
                  ))}
                </div>
              </section>

              {session.status === 'active' ? (
                <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                  <form className="space-y-3" onSubmit={submitAnswer}>
                    <textarea
                      required
                      className="min-h-36 w-full rounded-md border border-slate-300 p-3 text-sm leading-6 outline-none focus:border-slate-500"
                      placeholder="输入你的回答"
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                    />
                    <div className="flex flex-wrap gap-2">
                      <button className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60" disabled={answerMutation.isPending} type="submit">
                        {answerMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
                        提交回答
                      </button>
                      <button className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={unknownMutation.isPending} type="button" onClick={() => unknownMutation.mutate(session.id)}>
                        <HelpCircle className="size-4" />
                        不会
                      </button>
                      <button className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={skipMutation.isPending} type="button" onClick={() => skipMutation.mutate(session.id)}>
                        <SkipForward className="size-4" />
                        跳过本题
                      </button>
                    </div>
                  </form>
                  <div className="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                    <input
                      className="h-10 rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500"
                      placeholder="追问题意"
                      value={clarifyText}
                      onChange={(event) => setClarifyText(event.target.value)}
                    />
                    <button className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={clarifyMutation.isPending} type="button" onClick={() => clarifyMutation.mutate({ id: session.id, question: clarifyText })}>
                      追问题意
                    </button>
                  </div>
                </section>
              ) : (
                <EvaluationPanel session={session} onNext={startNextTask} hasNext={activeTasks.length > 0} />
              )}
            </>
          )}
        </main>
      </div>
    </section>
  )
}

function TaskButton({ active, disabled, task, onStart }: { active: boolean; disabled: boolean; task: ReviewTask; onStart: () => void }) {
  return (
    <button
      className={cn('block w-full px-4 py-3 text-left transition', active ? 'bg-emerald-50' : 'hover:bg-slate-50', disabled ? 'opacity-60' : '')}
      disabled={disabled}
      type="button"
      onClick={onStart}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="truncate text-sm font-medium text-slate-950">{task.pointTitle ?? task.manualPrompt}</span>
        <span className="shrink-0 text-xs text-slate-500">{task.estimatedMinutes}m</span>
      </div>
      <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
        <span>{task.typeLabel}</span>
        <span>/</span>
        <span>{task.statusLabel}</span>
      </div>
    </button>
  )
}

function StatusTag({ status }: { status: string }) {
  const label = status === 'evaluated' ? '已收口' : status === 'abandoned' ? '已跳过' : '进行中'
  return <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{label}</span>
}

function EvaluationPanel({ session, onNext, hasNext }: { session: ReviewSession; onNext: () => void; hasNext: boolean }) {
  const evaluation = session.evaluation
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-2 text-sm font-semibold text-slate-950">
        <CheckCircle2 className="size-4 text-emerald-600" />
        收口评分
      </div>
      {evaluation ? (
        <div className="mt-4 space-y-4">
          <div className="grid gap-3 sm:grid-cols-5">
            <Score label="结论" value={evaluation.score.conclusionAccuracy} />
            <Score label="机制" value={evaluation.score.mechanismExplanation} />
            <Score label="边界" value={evaluation.score.boundaryCases} />
            <Score label="迁移" value={evaluation.score.transferApplication} />
            <Score label="总分" value={evaluation.score.overall} strong />
          </div>
          <TextBlock title="总体评价" value={evaluation.overallComment} />
          <ListBlock title="正确点" values={evaluation.correctPoints} />
          <ListBlock title="遗漏点" values={evaluation.missingPoints} />
          <ListBlock title="不准确点" values={evaluation.inaccuratePoints} />
          <TextBlock title="两分钟参考回答" value={evaluation.referenceAnswer} />
          <ListBlock title="薄弱点" values={evaluation.weakPoints} />
        </div>
      ) : (
        <div className="mt-3 text-sm text-slate-500">本题已跳过，未更新掌握度。</div>
      )}
      <button className="mt-5 h-10 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60" disabled={!hasNext} type="button" onClick={onNext}>
        进入下一题
      </button>
    </section>
  )
}

function Score({ label, value, strong = false }: { label: string; value: number; strong?: boolean }) {
  return (
    <div className="rounded-md bg-slate-50 px-3 py-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className={cn('mt-1 text-lg font-semibold', strong ? 'text-emerald-700' : 'text-slate-950')}>{value.toFixed(1)}</div>
    </div>
  )
}

function TextBlock({ title, value }: { title: string; value: string }) {
  return (
    <div>
      <div className="text-sm font-semibold text-slate-950">{title}</div>
      <div className="mt-2 rounded-md bg-slate-50 px-3 py-2 text-sm leading-6 text-slate-700">{value}</div>
    </div>
  )
}

function ListBlock({ title, values }: { title: string; values: string[] }) {
  return <TextBlock title={title} value={values.length > 0 ? values.join(' / ') : '无'} />
}
