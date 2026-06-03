import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, CheckCircle2, HelpCircle, ListChecks, Loader2, PauseCircle, Play, Send, SkipForward } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import {
  answerReviewSession,
  clarifyReviewSession,
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
type SessionMode = 'manual' | 'immersive'

export function ReviewSessionPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [session, setSession] = useState<ReviewSession | null>(null)
  const [pendingTask, setPendingTask] = useState<ReviewTask | null>(null)
  const [answer, setAnswer] = useState('')
  const [clarifyText, setClarifyText] = useState('')
  const autoStartedTaskIdRef = useRef<string | null>(null)
  const todayQuery = useQuery({ queryKey: todayQueryKey, queryFn: getToday })
  const tasks = useMemo(
    () => todayQuery.data?.groups.flatMap((group) => group.tasks) ?? [],
    [todayQuery.data],
  )
  const activeTasks = useMemo(() => tasks.filter(isStartableTask), [tasks])
  const currentTaskId = session?.taskId ?? pendingTask?.id ?? null
  const currentTaskIndex = currentTaskId
    ? tasks.findIndex((task) => task.id === currentTaskId)
    : -1
  const handledTaskCount = tasks.filter((task) => !isStartableTask(task)).length
  const progressPosition = currentTaskIndex >= 0
    ? currentTaskIndex + 1
    : Math.min(tasks.length, handledTaskCount + (activeTasks.length > 0 ? 1 : 0))
  const progressPercent = tasks.length > 0
    ? Math.round((Math.max(handledTaskCount, progressPosition - 1) / tasks.length) * 100)
    : 0
  const mode: SessionMode = searchParams.get('mode') === 'immersive' ? 'immersive' : 'manual'
  const hasNextTask = session
    ? activeTasks.some((task) => task.id !== session.taskId)
    : activeTasks.length > 0

  const startMutation = useMutation({
    mutationFn: (task: ReviewTask) => startReviewSession(task.id),
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      setPendingTask(null)
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
    onError: () => setPendingTask(null),
  })
  const answerMutation = useMutation({
    mutationFn: ({ id, value }: { id: string; value: string }) => answerReviewSession(id, value),
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      setAnswer('')
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })
  const unknownMutation = useMutation({
    mutationFn: unknownReviewSession,
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      await queryClient.invalidateQueries({ queryKey: todayQueryKey })
    },
  })
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
    getApiErrorMessage(startMutation.error, '') ||
    getApiErrorMessage(answerMutation.error, '') ||
    getApiErrorMessage(unknownMutation.error, '') ||
    getApiErrorMessage(clarifyMutation.error, '') ||
    getApiErrorMessage(skipMutation.error, '') ||
    (todayQuery.isError ? getApiErrorMessage(todayQuery.error) : '')

  const startTask = useCallback((task: ReviewTask) => {
    setSession(null)
    setPendingTask(task)
    startMutation.mutate(task)
  }, [startMutation])

  useEffect(() => {
    if (
      mode !== 'immersive' ||
      todayQuery.isLoading ||
      startMutation.isPending ||
      session ||
      pendingTask
    ) {
      return
    }
    const next = activeTasks[0]
    if (next && next.id !== autoStartedTaskIdRef.current) {
      autoStartedTaskIdRef.current = next.id
      const timeoutId = window.setTimeout(() => startTask(next), 0)
      return () => window.clearTimeout(timeoutId)
    }
  }, [
    activeTasks,
    mode,
    pendingTask,
    session,
    startTask,
    startMutation.isPending,
    todayQuery.isLoading,
  ])

  function submitAnswer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    answerMutation.mutate({ id: session.id, value: answer })
  }

  function startNextTask() {
    const currentIndex = session
      ? tasks.findIndex((task) => task.id === session.taskId)
      : -1
    const nextAfterCurrent = currentIndex >= 0
      ? tasks.slice(currentIndex + 1).find(isStartableTask)
      : undefined
    const next = nextAfterCurrent ??
      activeTasks.find((task) => task.id !== session?.taskId) ??
      activeTasks[0]
    if (next) startTask(next)
  }

  function enterImmersiveMode() {
    setSearchParams({ mode: 'immersive' })
    if (!session && !pendingTask && activeTasks[0]) {
      autoStartedTaskIdRef.current = activeTasks[0].id
      startTask(activeTasks[0])
    }
  }

  function exitImmersiveMode() {
    autoStartedTaskIdRef.current = null
    setSearchParams({})
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-3xl font-semibold text-slate-950">复习会话</h1>
          <div className="mt-1 text-sm text-slate-500">
            {mode === 'immersive' ? '沉浸式推进今日队列' : '手动选择今日任务'}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            className={cn(
              'inline-flex h-10 items-center gap-2 rounded-md px-3 text-sm font-medium shadow-sm disabled:opacity-60',
              mode === 'manual'
                ? 'bg-slate-900 text-white hover:bg-slate-800'
                : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )}
            type="button"
            onClick={exitImmersiveMode}
          >
            <ListChecks className="size-4" />
            手动队列
          </button>
          <button
            className={cn(
              'inline-flex h-10 items-center gap-2 rounded-md px-3 text-sm font-medium shadow-sm disabled:opacity-60',
              mode === 'immersive'
                ? 'bg-emerald-700 text-white hover:bg-emerald-800'
                : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )}
            type="button"
            onClick={enterImmersiveMode}
          >
            <Play className="size-4" />
            沉浸式
          </button>
          <button
            className="inline-flex h-10 w-fit items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            type="button"
            onClick={() => navigate('/today')}
          >
            <ArrowLeft className="size-4" />
            返回今日计划
          </button>
        </div>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className={cn('grid gap-5', mode === 'immersive' ? 'xl:grid-cols-1' : 'xl:grid-cols-[320px_minmax(0,1fr)]')}>
        {mode === 'manual' ? (
          <aside className="h-fit rounded-lg border border-slate-200 bg-white shadow-sm xl:sticky xl:top-24">
            <div className="border-b border-slate-200 px-4 py-3">
              <h2 className="text-sm font-semibold text-slate-950">今日队列</h2>
              <div className="mt-1 text-xs text-slate-500">{activeTasks.length} 个待复习任务</div>
            </div>
            <div className="max-h-[620px] divide-y divide-slate-100 overflow-auto">
              {tasks.length === 0 ? (
                <div className="p-4 text-sm text-slate-500">暂无今日任务。</div>
              ) : (
                tasks.map((task) => (
                  <TaskButton
                    key={task.id}
                    active={session?.taskId === task.id || pendingTask?.id === task.id}
                    disabled={startMutation.isPending || !isStartableTask(task)}
                    task={task}
                    onStart={() => startTask(task)}
                  />
                ))
              )}
            </div>
          </aside>
        ) : null}

        <main className="space-y-4">
          {mode === 'immersive' ? (
            <ImmersiveProgress
              activeCount={activeTasks.length}
              handledCount={handledTaskCount}
              onExit={exitImmersiveMode}
              percent={progressPercent}
              position={progressPosition}
              total={tasks.length}
            />
          ) : null}

          {!session && !pendingTask ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              <div>
                {todayQuery.isLoading
                  ? '正在加载今日队列...'
                  : tasks.length === 0
                    ? '暂无今日任务，请先返回今日计划补齐队列。'
                    : activeTasks.length === 0
                      ? '今日任务已完成或跳过。'
                      : mode === 'immersive'
                        ? '正在准备第一道题。'
                        : '请选择一个今日任务开始严格面试复习。'}
              </div>
              {mode === 'immersive' && activeTasks.length > 0 ? (
                <button
                  className="mt-4 inline-flex h-10 items-center gap-2 rounded-md bg-emerald-700 px-3 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
                  type="button"
                  disabled={startMutation.isPending}
                  onClick={() => startTask(activeTasks[0])}
                >
                  {startMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
                  开始沉浸式复习
                </button>
              ) : null}
              {todayQuery.isLoading ? null : tasks.length === 0 || activeTasks.length === 0 ? (
                <button
                  className="mt-4 inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800"
                  type="button"
                  onClick={() => navigate('/today')}
                >
                  <ArrowLeft className="size-4" />
                  返回今日计划
                </button>
              ) : null}
            </div>
          ) : (
            <>
              <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div>
                    <div className="text-xs font-medium text-slate-500">{session?.topicTitle ?? pendingTask?.topicTitle ?? '今日加练'}</div>
                    <h2 className="mt-1 text-xl font-semibold text-slate-950">
                      {session?.pointTitle ?? session?.manualPrompt ?? pendingTask?.pointTitle ?? pendingTask?.manualPrompt}
                    </h2>
                  </div>
                  <StatusTag status={session?.status ?? 'generating'} />
                </div>
              </section>

              <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-950">
                  对话记录
                </div>
                <div className="space-y-3 p-5">
                  {pendingTask && startMutation.isPending ? <AiThinking /> : null}
                  {session?.turns.map((turn) => <TurnCard key={turn.id} turn={turn} />)}
                </div>
              </section>

              {session?.status === 'active' ? (
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
                session ? (
                  <EvaluationPanel
                    session={session}
                    onNext={startNextTask}
                    hasNext={hasNextTask}
                    nextLabel={mode === 'immersive' ? '继续下一题' : '进入下一题'}
                  />
                ) : null
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

function ImmersiveProgress({
  activeCount,
  handledCount,
  onExit,
  percent,
  position,
  total,
}: {
  activeCount: number
  handledCount: number
  onExit: () => void
  percent: number
  position: number
  total: number
}) {
  return (
    <section className="rounded-lg border border-emerald-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold text-emerald-800">
            <Play className="size-4" aria-hidden="true" />
            沉浸式复习
          </div>
          <div className="mt-2 text-2xl font-semibold text-slate-950">
            {total === 0 ? '暂无任务' : `第 ${Math.max(1, position)} / ${total} 题`}
          </div>
          <div className="mt-1 text-sm text-slate-500">
            已处理 {handledCount} 项 · 剩余 {activeCount} 项
          </div>
        </div>
        <button
          type="button"
          onClick={onExit}
          className="inline-flex h-10 w-fit items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <PauseCircle className="size-4" aria-hidden="true" />
          退出沉浸式
        </button>
      </div>
      <div className="mt-4 h-2 overflow-hidden rounded-full bg-emerald-50">
        <div
          className="h-full rounded-full bg-emerald-600 transition-all"
          style={{ width: `${percent}%` }}
        />
      </div>
    </section>
  )
}

function StatusTag({ status }: { status: string }) {
  const label = status === 'generating' ? '生成中' : status === 'evaluated' ? '已收口' : status === 'abandoned' ? '已跳过' : '进行中'
  return <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{label}</span>
}

function AiThinking() {
  return (
    <div className="rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-950">
      <div className="flex items-center gap-2 text-xs font-semibold text-emerald-700">
        <span className="h-2 w-2 rounded-full bg-emerald-500" />
        面试官
      </div>
      <div className="mt-3 flex items-center gap-3">
        <div className="flex h-8 items-center gap-1 rounded-full bg-white px-3 shadow-sm shadow-emerald-950/5">
          {[0, 1, 2].map((index) => (
            <span
              key={index}
              className="h-1.5 w-1.5 animate-bounce rounded-full bg-emerald-500"
              style={{ animationDelay: `${index * 120}ms` }}
            />
          ))}
        </div>
        <span className="text-sm text-emerald-800">正在生成针对当前复习点的题目</span>
      </div>
    </div>
  )
}

function TurnCard({ turn }: { turn: ReviewSession['turns'][number] }) {
  const isUser = turn.role === 'user'
  const isQuestion = turn.turnType === 'question' || turn.turnType === 'follow_up'
  return (
    <div className={cn('rounded-lg border bg-white px-4 py-3 text-sm shadow-sm', isUser ? 'border-slate-200 text-slate-900' : 'border-emerald-100 text-slate-900')}>
      <div className="mb-2 flex items-center justify-between gap-3">
        <div className={cn('text-xs font-semibold', isUser ? 'text-slate-500' : 'text-emerald-700')}>
          {isUser ? '我' : '面试官'}
        </div>
        <div className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500">{turnTypeLabel(turn.turnType)}</div>
      </div>
      <MessageContent content={turn.content} prominent={isQuestion && !isUser} />
    </div>
  )
}

function MessageContent({ content, prominent }: { content: string; prominent: boolean }) {
  const blocks = parseMarkdownLite(content)
  return (
    <div className={cn('space-y-3 leading-6 text-slate-800', prominent ? 'text-sm' : 'text-sm')}>
      {blocks.map((block, index) => {
        if (block.type === 'code') {
          return <CodeBlock key={index} content={block.content} />
        }
        if (block.type === 'list') {
          return (
            <ol key={index} className="space-y-1 rounded-md bg-slate-50 px-4 py-3 pl-7 text-sm text-slate-800">
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex} className="list-decimal pl-1">
                  <InlineCode text={item} />
                </li>
              ))}
            </ol>
          )
        }
        return (
          <p key={index} className={cn('text-slate-800', prominent ? 'font-medium' : '')}>
            <InlineCode text={block.content} />
          </p>
        )
      })}
    </div>
  )
}

function CodeBlock({ content }: { content: string }) {
  const [expanded, setExpanded] = useState(false)
  const lines = content.split('\n')
  const collapsible = lines.length > 18
  const visibleContent = !collapsible || expanded ? content : lines.slice(0, 18).join('\n')
  return (
    <div className="overflow-hidden rounded-md border border-slate-200 bg-slate-50">
      <pre className="overflow-x-auto px-3 py-2 font-mono text-[12px] leading-5 text-slate-800">
        <code>{visibleContent}</code>
      </pre>
      {collapsible ? (
        <button
          className="h-9 w-full border-t border-slate-200 bg-white text-xs font-medium text-slate-600 hover:bg-slate-50"
          type="button"
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? '收起代码' : `展开全部 ${lines.length} 行代码`}
        </button>
      ) : null}
    </div>
  )
}

function InlineCode({ text }: { text: string }) {
  const parts = text.split(/(`[^`]+`)/g).filter(Boolean)
  return (
    <>
      {parts.map((part, index) =>
        part.startsWith('`') && part.endsWith('`') ? (
          <code key={index} className="rounded bg-emerald-50 px-1.5 py-0.5 font-mono text-[0.9em] text-emerald-800 ring-1 ring-emerald-200">
            {part.slice(1, -1)}
          </code>
        ) : (
          <span key={index}>{part}</span>
        ),
      )}
    </>
  )
}

type MessageBlock =
  | { type: 'paragraph'; content: string }
  | { type: 'code'; content: string }
  | { type: 'list'; items: string[] }

function parseMarkdownLite(content: string): MessageBlock[] {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const blocks: MessageBlock[] = []
  let paragraph: string[] = []
  let list: string[] = []
  let code: string[] = []
  let inCode = false

  function flushParagraph() {
    if (paragraph.length > 0) {
      blocks.push({ type: 'paragraph', content: paragraph.join(' ') })
      paragraph = []
    }
  }

  function flushList() {
    if (list.length > 0) {
      blocks.push({ type: 'list', items: list })
      list = []
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()
    if (line.trim().startsWith('```')) {
      if (inCode) {
        blocks.push({ type: 'code', content: code.join('\n').trimEnd() })
        code = []
        inCode = false
      } else {
        flushParagraph()
        flushList()
        inCode = true
      }
      continue
    }

    if (inCode) {
      code.push(rawLine)
      continue
    }

    if (!line.trim()) {
      flushParagraph()
      flushList()
      continue
    }

    const listMatch = line.match(/^\s*\d+[.、]\s+(.+)$/)
    if (listMatch) {
      flushParagraph()
      list.push(listMatch[1])
      continue
    }

    flushList()
    paragraph.push(line.trim())
  }

  if (inCode && code.length > 0) {
    blocks.push({ type: 'code', content: code.join('\n').trimEnd() })
  }
  flushParagraph()
  flushList()
  return blocks.length > 0 ? blocks : [{ type: 'paragraph', content }]
}

function turnTypeLabel(type: string) {
  const labels: Record<string, string> = {
    question: '题目',
    follow_up: '追问',
    answer: '回答',
    unknown: '不会',
    clarification: '澄清',
    skip: '跳过',
    evaluation: '评分',
  }
  return labels[type] ?? type
}

function EvaluationPanel({
  hasNext,
  nextLabel,
  onNext,
  session,
}: {
  hasNext: boolean
  nextLabel: string
  onNext: () => void
  session: ReviewSession
}) {
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
        {hasNext ? nextLabel : '今日队列已完成'}
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

function isStartableTask(task: ReviewTask) {
  return task.status === 'pending' || task.status === 'in_progress'
}
