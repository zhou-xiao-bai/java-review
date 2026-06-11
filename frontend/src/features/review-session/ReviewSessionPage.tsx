import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, CheckCircle2, HelpCircle, ListChecks, Loader2, PauseCircle, Play, Send, SkipForward } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import {
  answerReviewSession,
  clarifyReviewSession,
  getApiErrorMessage,
  getTodayQueue,
  skipReviewSession,
  startReviewSession,
  unknownReviewSession,
  type ReviewPlanExplanation,
  type ReviewSession,
  type TodayQueueItem,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const todayQueueQueryKey = ['today-queue'] as const
type SessionMode = 'manual' | 'immersive'

export function ReviewSessionPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [session, setSession] = useState<ReviewSession | null>(null)
  const [selectedItem, setSelectedItem] = useState<TodayQueueItem | null>(null)
  const [pendingItem, setPendingItem] = useState<TodayQueueItem | null>(null)
  const [answer, setAnswer] = useState('')
  const [clarifyText, setClarifyText] = useState('')
  const [pendingAnswerPreview, setPendingAnswerPreview] = useState<string | null>(null)
  const [pendingClarifyPreview, setPendingClarifyPreview] = useState<string | null>(null)
  const [immersiveQueueSnapshotIds, setImmersiveQueueSnapshotIds] = useState<string[]>([])
  const autoStartedStateIdRef = useRef<string | null>(null)
  const transcriptEndRef = useRef<HTMLDivElement | null>(null)
  const queueQuery = useQuery({
    queryKey: todayQueueQueryKey,
    queryFn: getTodayQueue,
  })
  const items = useMemo(
    () => queueQuery.data?.groups.flatMap((group) => group.items) ?? [],
    [queueQuery.data],
  )
  const activeItems = items
  const mode: SessionMode = searchParams.get('mode') === 'immersive' ? 'immersive' : 'manual'
  const currentStateId = session?.reviewUnitStateId ?? pendingItem?.stateId ?? selectedItem?.stateId ?? null
  const snapshotActiveCount = immersiveQueueSnapshotIds.filter((stateId) =>
    items.some((item) => item.stateId === stateId),
  ).length
  const immersiveTotal = immersiveQueueSnapshotIds.length > 0
    ? immersiveQueueSnapshotIds.length
    : items.length
  const handledItemCount = immersiveTotal > 0
    ? Math.max(0, immersiveTotal - snapshotActiveCount)
    : 0
  const progressPosition = Math.min(
    immersiveTotal,
    handledItemCount + (currentStateId || activeItems.length > 0 ? 1 : 0),
  )
  const progressPercent = immersiveTotal > 0
    ? Math.round((handledItemCount / immersiveTotal) * 100)
    : 0
  const requestedStateId = searchParams.get('stateId')
  const hasNextTask = session
    ? activeItems.some((item) => item.stateId !== session.reviewUnitStateId)
    : activeItems.length > 0

  const startMutation = useMutation({
    mutationFn: (item: TodayQueueItem) => startReviewSession(item.stateId),
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      setPendingItem(null)
      await queryClient.invalidateQueries({ queryKey: todayQueueQueryKey })
    },
    onError: (_error, item) => {
      setPendingItem(null)
      setSelectedItem(item)
    },
  })
  const answerMutation = useMutation({
    mutationFn: ({ id, value }: { id: string; value: string }) => answerReviewSession(id, value),
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      setAnswer('')
      setPendingAnswerPreview(null)
      await queryClient.invalidateQueries({ queryKey: todayQueueQueryKey })
    },
    onError: () => setPendingAnswerPreview(null),
  })
  const unknownMutation = useMutation({
    mutationFn: unknownReviewSession,
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      await queryClient.invalidateQueries({ queryKey: todayQueueQueryKey })
    },
  })
  const clarifyMutation = useMutation({
    mutationFn: ({ id, question }: { id: string; question: string }) => clarifyReviewSession(id, question),
    onSuccess: (nextSession) => {
      setSession(nextSession)
      setClarifyText('')
      setPendingClarifyPreview(null)
    },
    onError: () => setPendingClarifyPreview(null),
  })
  const skipMutation = useMutation({
    mutationFn: skipReviewSession,
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      await queryClient.invalidateQueries({ queryKey: todayQueueQueryKey })
    },
  })
  const actionPending =
    answerMutation.isPending ||
    clarifyMutation.isPending ||
    unknownMutation.isPending ||
    skipMutation.isPending

  const errorMessage =
    getApiErrorMessage(startMutation.error, '') ||
    getApiErrorMessage(answerMutation.error, '') ||
    getApiErrorMessage(unknownMutation.error, '') ||
    getApiErrorMessage(clarifyMutation.error, '') ||
    getApiErrorMessage(skipMutation.error, '') ||
    (queueQuery.isError ? getApiErrorMessage(queueQuery.error) : '')

  const startItem = useCallback((item: TodayQueueItem) => {
    setSession(null)
    setSelectedItem(null)
    setPendingItem(item)
    startMutation.mutate(item)
  }, [startMutation])

  const selectItem = useCallback((
    item: TodayQueueItem,
    syncUrl = true,
  ) => {
    setSession(null)
    setPendingItem(null)
    setSelectedItem(item)
    if (syncUrl) {
      setSearchParams({ stateId: item.stateId })
    }
  }, [setSearchParams])

  useEffect(() => {
    if (mode === 'immersive' || immersiveQueueSnapshotIds.length === 0) {
      return
    }
    const timeoutId = window.setTimeout(() => setImmersiveQueueSnapshotIds([]), 0)
    return () => window.clearTimeout(timeoutId)
  }, [immersiveQueueSnapshotIds.length, mode])

  useEffect(() => {
    if (
      mode !== 'immersive' ||
      queueQuery.isLoading ||
      startMutation.isPending ||
      session ||
      pendingItem ||
      selectedItem
    ) {
      return
    }
    const next = pickRandomItem(activeItems, autoStartedStateIdRef.current)
    if (next && next.stateId !== autoStartedStateIdRef.current) {
      autoStartedStateIdRef.current = next.stateId
      const snapshotIds = immersiveQueueSnapshotIds.length === 0
        ? activeItems.map((item) => item.stateId)
        : null
      const timeoutId = window.setTimeout(() => {
        if (snapshotIds) {
          setImmersiveQueueSnapshotIds(snapshotIds)
        }
        startItem(next)
      }, 0)
      return () => window.clearTimeout(timeoutId)
    }
  }, [
    activeItems,
    immersiveQueueSnapshotIds.length,
    mode,
    pendingItem,
    selectedItem,
    session,
    startItem,
    startMutation.isPending,
    queueQuery.isLoading,
  ])

  useEffect(() => {
    if (
      !requestedStateId ||
      queueQuery.isLoading ||
      startMutation.isPending ||
      session ||
      pendingItem
    ) {
      return
    }
    const requestedItem = activeItems.find((item) => item.stateId === requestedStateId)
    if (requestedItem && selectedItem?.stateId !== requestedItem.stateId) {
      const timeoutId = window.setTimeout(() => selectItem(requestedItem, false), 0)
      return () => window.clearTimeout(timeoutId)
    }
  }, [
    activeItems,
    pendingItem,
    queueQuery.isLoading,
    requestedStateId,
    selectedItem?.stateId,
    selectItem,
    session,
    startMutation.isPending,
  ])

  useEffect(() => {
    transcriptEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [
    session?.turns.length,
    pendingAnswerPreview,
    pendingClarifyPreview,
    pendingItem,
    selectedItem,
    startMutation.isPending,
  ])

  function submitAnswer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    const value = answer.trim()
    if (!value || answerMutation.isPending) return
    setPendingAnswerPreview(value)
    answerMutation.mutate({ id: session.id, value })
  }

  function submitClarification() {
    if (!session || clarifyMutation.isPending) return
    const question = clarifyText.trim()
    setPendingClarifyPreview(question || '请解释题意。')
    clarifyMutation.mutate({ id: session.id, question })
  }

  function startNextTask() {
    const next = pickRandomItem(activeItems, session?.reviewUnitStateId)
    if (!next) {
      return
    }
    if (mode === 'immersive') {
      startItem(next)
    } else {
      selectItem(next)
    }
  }

  function enterImmersiveMode() {
    setSearchParams({ mode: 'immersive' })
    setSelectedItem(null)
    if (items.length > 0 && immersiveQueueSnapshotIds.length === 0) {
      setImmersiveQueueSnapshotIds(items.map((item) => item.stateId))
    }
    const next = pickRandomItem(activeItems)
    if (!session && !pendingItem && next) {
      autoStartedStateIdRef.current = next.stateId
      startItem(next)
    }
  }

  function exitImmersiveMode() {
    autoStartedStateIdRef.current = null
    setImmersiveQueueSnapshotIds([])
    setSearchParams({})
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-3xl font-semibold text-slate-950">复习会话</h1>
          <div className="mt-1 text-sm text-slate-500">
            {mode === 'immersive' ? '沉浸式随机抽取今日队列' : '手动选择今日任务'}
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
              <div className="mt-1 text-xs text-slate-500">{activeItems.length} 个待复习单元</div>
            </div>
            <div className="max-h-[620px] divide-y divide-slate-100 overflow-auto">
              {items.length === 0 ? (
                <div className="p-4 text-sm text-slate-500">暂无今日复习单元。</div>
              ) : (
                items.map((item) => (
                  <QueueItemButton
                    key={item.stateId}
                    active={currentStateId === item.stateId}
                    disabled={startMutation.isPending}
                    item={item}
                    onStart={() => selectItem(item)}
                  />
                ))
              )}
            </div>
          </aside>
        ) : null}

        <main className="space-y-4">
          {mode === 'immersive' ? (
            <ImmersiveProgress
              activeCount={snapshotActiveCount}
              handledCount={handledItemCount}
              onExit={exitImmersiveMode}
              percent={progressPercent}
              position={progressPosition}
              total={immersiveTotal}
            />
          ) : null}

          {!session && !pendingItem && !selectedItem ? (
            <div className="rounded-lg border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
              <div>
                {queueQuery.isLoading
                  ? '正在加载今日队列...'
                  : items.length === 0
                    ? '暂无今日复习单元。'
                    : activeItems.length === 0
                      ? '今日复习单元已处理。'
                      : mode === 'immersive'
                        ? '正在随机准备第一道题。'
                        : '请选择一个复习单元开始严格面试复习。'}
              </div>
              {mode === 'immersive' && activeItems.length > 0 ? (
                <button
                  className="mt-4 inline-flex h-10 items-center gap-2 rounded-md bg-emerald-700 px-3 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
                  type="button"
                  disabled={startMutation.isPending}
                  onClick={() => {
                    const next = pickRandomItem(activeItems)
                    if (next) startItem(next)
                  }}
                >
                  {startMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
                  开始沉浸式复习
                </button>
              ) : null}
              {queueQuery.isLoading ? null : items.length === 0 || activeItems.length === 0 ? (
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
                    <div className="text-xs font-medium text-slate-500">{session?.topicTitle ?? pendingItem?.scopeTitle ?? selectedItem?.scopeTitle}</div>
                    <h2 className="mt-1 text-xl font-semibold text-slate-950">
                      {session?.pointTitle ?? pendingItem?.unitTitle ?? selectedItem?.unitTitle}
                    </h2>
                  </div>
                  <StatusTag status={session?.status ?? (pendingItem ? 'generating' : 'ready')} />
                </div>
              </section>

              {selectedItem && !pendingItem && !session ? (
                <StartConfirmationPanel
                  item={selectedItem}
                  loading={startMutation.isPending}
                  onBack={() => navigate('/today')}
                  onStart={() => startItem(selectedItem)}
                />
              ) : null}

              {session || pendingItem ? (
                <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
                  <div className="border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-950">
                    对话记录
                  </div>
                  <div className="space-y-3 p-5">
                    {pendingItem && startMutation.isPending ? (
                      <AssistantThinking text="正在生成针对当前复习点的题目" />
                    ) : null}
                    {session?.turns
                      .filter((turn) => turn.turnType !== 'evaluation')
                      .map((turn) => <TurnCard key={turn.id} turn={turn} />)}
                    {pendingAnswerPreview ? (
                      <>
                        <PendingUserTurn content={pendingAnswerPreview} type="回答" />
                        <AssistantThinking text="正在拆解你的回答，判断要追问还是收口评价" />
                      </>
                    ) : null}
                    {pendingClarifyPreview ? (
                      <>
                        <PendingUserTurn content={pendingClarifyPreview} type="澄清" />
                        <AssistantThinking text="正在解释题意和回答维度，不展开标准答案" />
                      </>
                    ) : null}
                    {unknownMutation.isPending ? (
                      <>
                        <PendingUserTurn content="不会" type="不会" />
                        <AssistantThinking text="正在收口评价，并生成复习记录" />
                      </>
                    ) : null}
                    {skipMutation.isPending ? (
                      <PendingUserTurn content="跳过本题" type="跳过" />
                    ) : null}
                    <div ref={transcriptEndRef} />
                  </div>
                </section>
              ) : null}

              {session?.status === 'active' ? (
                <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                  <form className="space-y-3" onSubmit={submitAnswer}>
                    <textarea
                      required
                      className="min-h-36 w-full rounded-md border border-slate-300 p-3 text-sm leading-6 outline-none focus:border-slate-500 disabled:bg-slate-50 disabled:text-slate-500"
                      disabled={actionPending}
                      placeholder="输入你的回答"
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                    />
                    <div className="flex flex-wrap gap-2">
                      <button className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60" disabled={actionPending || !answer.trim()} type="submit">
                        {answerMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
                        {answerMutation.isPending ? '正在分析' : '提交回答'}
                      </button>
                      <button className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={actionPending} type="button" onClick={() => unknownMutation.mutate(session.id)}>
                        {unknownMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <HelpCircle className="size-4" />}
                        {unknownMutation.isPending ? '正在收口' : '不会'}
                      </button>
                      <button className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={actionPending} type="button" onClick={() => skipMutation.mutate(session.id)}>
                        {skipMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <SkipForward className="size-4" />}
                        {skipMutation.isPending ? '正在跳过' : '跳过本题'}
                      </button>
                    </div>
                  </form>
                  <div className="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                    <input
                      className="h-10 rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500 disabled:bg-slate-50 disabled:text-slate-500"
                      disabled={actionPending}
                      placeholder="追问题意"
                      value={clarifyText}
                      onChange={(event) => setClarifyText(event.target.value)}
                    />
                    <button className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={actionPending} type="button" onClick={submitClarification}>
                      {clarifyMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <HelpCircle className="size-4" />}
                      {clarifyMutation.isPending ? '正在澄清' : '追问题意'}
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

function QueueItemButton({
  active,
  disabled,
  item,
  onStart,
}: {
  active: boolean
  disabled: boolean
  item: TodayQueueItem
  onStart: () => void
}) {
  return (
    <button
      className={cn('block w-full px-4 py-3 text-left transition', active ? 'bg-emerald-50' : 'hover:bg-slate-50', disabled ? 'opacity-60' : '')}
      disabled={disabled}
      type="button"
      onClick={onStart}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="truncate text-sm font-medium text-slate-950">{item.unitTitle}</span>
        <span className="shrink-0 text-xs text-slate-500">{item.reasonLabel}</span>
      </div>
      <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
        <span>{item.scopeTitle}</span>
        <span>/</span>
        <span>{item.domainName}</span>
      </div>
    </button>
  )
}

function StartConfirmationPanel({
  item,
  loading,
  onBack,
  onStart,
}: {
  item: TodayQueueItem
  loading: boolean
  onBack: () => void
  onStart: () => void
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="text-sm font-semibold text-slate-950">确认复习单元</div>
          <div className="mt-2 text-sm leading-6 text-slate-600">
            {item.scopeTitle} / {item.domainName}
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <ReviewUnitMeta label="队列原因" value={item.reasonLabel} />
            <ReviewUnitMeta label="重要度" value={String(item.importance)} />
            <ReviewUnitMeta label="难度" value={String(item.difficulty)} />
            <ReviewUnitMeta label="频率" value={String(item.interviewFrequency)} />
            <ReviewUnitMeta label="上次结果" value={reviewResultLabel(item.lastResult) || '无'} />
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <button
            type="button"
            className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            disabled={loading}
            onClick={onBack}
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            返回今日计划
          </button>
          <button
            type="button"
            className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
            disabled={loading}
            onClick={onStart}
          >
            {loading ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Play className="size-4" aria-hidden="true" />}
            {loading ? '正在生成' : '生成题目'}
          </button>
        </div>
      </div>
    </section>
  )
}

function ReviewUnitMeta({ label, value }: { label: string; value: string }) {
  return (
    <span className="rounded-md bg-slate-50 px-2.5 py-1.5 text-xs font-medium text-slate-600">
      {label} {value}
    </span>
  )
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

function pickRandomItem(
  items: TodayQueueItem[],
  excludedStateId?: string | null,
) {
  const candidates = excludedStateId
    ? items.filter((item) => item.stateId !== excludedStateId)
    : items
  const pool = candidates.length > 0 ? candidates : items
  if (pool.length === 0) {
    return null
  }
  return pool[Math.floor(Math.random() * pool.length)]
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
  const label =
    status === 'ready'
      ? '待确认'
      : status === 'generating'
        ? '生成中'
        : status === 'evaluated'
          ? '已收口'
          : status === 'abandoned'
            ? '已跳过'
            : '进行中'
  return <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{label}</span>
}

function AssistantThinking({ text }: { text: string }) {
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
        <span className="text-sm text-emerald-800">{text}</span>
      </div>
    </div>
  )
}

function PendingUserTurn({ content, type }: { content: string; type: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 shadow-sm">
      <div className="mb-2 flex items-center justify-between gap-3">
        <div className="text-xs font-semibold text-slate-500">我</div>
        <div className="rounded bg-white px-2 py-0.5 text-xs text-slate-500">{type} · 发送中</div>
      </div>
      <MessageContent content={content} prominent={false} />
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
  const corrections = compactCorrections(evaluation?.corrections ?? [])
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-950">
          <CheckCircle2 className="size-4 text-emerald-600" />
          收口评价
        </div>
        <NextTaskButton disabled={!hasNext} label={hasNext ? nextLabel : '今日队列已完成'} onClick={onNext} />
      </div>
      {evaluation ? (
        <div className="mt-4 space-y-4">
          <div className="rounded-md bg-slate-50 px-3 py-3">
            <div className="text-xs font-medium text-slate-500">本题判断</div>
            <div className="mt-1 text-sm font-semibold text-slate-950">{nextStatusLabel(evaluation.nextStatus)}</div>
            <div className="mt-2 text-sm leading-6 text-slate-700">{evaluation.overallComment}</div>
          </div>
          <CorrectionList corrections={corrections} referenceAnswer={evaluation.referenceAnswer} />
          {evaluation.masteryCard ? (
            <div>
              <div className="text-sm font-semibold text-slate-950">复习记录</div>
              <div className="mt-2 space-y-3 rounded-md bg-emerald-50 px-3 py-3 text-sm text-emerald-950">
                <div className="font-medium">{evaluation.masteryCard.oneSentence}</div>
                <DiagnosticList title="薄弱点与关键记忆" values={evaluation.masteryCard.mustRemember} tone="plain" />
                <TextBlock title="复习计划" value={reviewPlanText(evaluation.nextStatus, evaluation.masteryCard.nextProbe, session.nextReviewAt)} />
                <PlanExplanation explanation={session.reviewPlanExplanation} />
              </div>
            </div>
          ) : null}
        </div>
      ) : (
        <div className="mt-3 text-sm text-slate-500">本题已跳过，未更新掌握度。</div>
      )}
    </section>
  )
}

function NextTaskButton({ disabled, label, onClick }: { disabled: boolean; label: string; onClick: () => void }) {
  return (
    <button className="h-10 w-fit rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60" disabled={disabled} type="button" onClick={onClick}>
      {label}
    </button>
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

type CorrectionItem = {
  userIssue: string
  correctAnswer: string
  explanation?: string | null
}

function CorrectionList({ corrections, referenceAnswer }: { corrections: CorrectionItem[]; referenceAnswer: string }) {
  const answer = referenceAnswer.trim()
  if (corrections.length === 0 && !answer) {
    return null
  }
  return (
    <div className="rounded-md bg-amber-50 px-3 py-3 text-sm text-amber-950">
      <div className="flex items-center gap-2 font-semibold">
        <AlertCircle className="size-4" aria-hidden="true" />
        纠错与正确答案
      </div>
      {corrections.length > 0 ? (
        <div className="mt-3 divide-y divide-amber-100">
          {corrections.map((correction, index) => (
            <div key={`${correction.userIssue}-${index}`} className="py-3 first:pt-0 last:pb-0">
              <div className="text-xs font-semibold text-amber-700">问题</div>
              <div className="mt-1 leading-6">{correction.userIssue}</div>
              <div className="mt-2 text-xs font-semibold text-emerald-700">正确说法</div>
              <div className="mt-1 leading-6 text-slate-900">{correction.correctAnswer}</div>
              {correction.explanation ? (
                <div className="mt-2 leading-6 text-amber-900">{correction.explanation}</div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
      {answer ? (
        <div className="mt-3 rounded-md bg-white/70 px-3 py-3">
          <div className="text-xs font-semibold text-emerald-700">完整答法</div>
          <div className="mt-1 leading-6 text-slate-900">{answer}</div>
        </div>
      ) : null}
    </div>
  )
}

function DiagnosticList({
  empty = '无',
  title,
  tone,
  values,
}: {
  empty?: string
  title: string
  tone: 'good' | 'warning' | 'plain'
  values: string[]
}) {
  const items = compactValues(values)
  return (
    <div className={cn('rounded-md px-3 py-3 text-sm', tone === 'good' ? 'bg-emerald-50 text-emerald-950' : tone === 'warning' ? 'bg-amber-50 text-amber-950' : 'bg-white/60 text-emerald-950')}>
      <div className="font-semibold">{title}</div>
      {items.length > 0 ? (
        <ul className="mt-2 space-y-1.5">
          {items.map((item) => (
            <li key={item} className="leading-6">{item}</li>
          ))}
        </ul>
      ) : (
        <div className="mt-2 leading-6 opacity-80">{empty}</div>
      )}
    </div>
  )
}

function PlanExplanation({ explanation }: { explanation: ReviewPlanExplanation | null }) {
  if (!explanation) {
    return null
  }
  const factors = explanation.priorityFactors.filter((factor) => factor.key !== 'overdue' || factor.contribution !== 0)
  return (
    <div className="rounded-md bg-white/70 px-3 py-3 text-sm text-slate-800">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div className="font-semibold text-slate-950">计划依据</div>
        <div className="text-xs font-medium text-emerald-700">优先级 {formatPlanScore(explanation.priorityScore)}</div>
      </div>
      <div className="mt-2 text-xs leading-5 text-slate-600">
        {explanation.scheduleRule}
        {explanation.nextReviewAtText ? ` · ${explanation.nextReviewAtText}` : ''}。{explanation.scheduleReason}
      </div>
      {factors.length > 0 ? (
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {factors.map((factor) => (
            <div key={factor.key} className="rounded-md border border-emerald-100 bg-white px-2.5 py-2">
              <div className="flex items-center justify-between gap-2">
                <div className="text-xs font-semibold text-slate-700">{factor.label}</div>
                <div className="shrink-0 text-xs font-medium text-slate-500">
                  {factor.value} / {formatContribution(factor.contribution)}
                </div>
              </div>
              <div className="mt-1 text-xs leading-5 text-slate-500">{factor.description}</div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function compactCorrections(corrections: CorrectionItem[]) {
  const seen = new Set<string>()
  return corrections
    .map((correction) => ({
      userIssue: correction.userIssue?.trim() ?? '',
      correctAnswer: correction.correctAnswer?.trim() ?? '',
      explanation: correction.explanation?.trim() ?? '',
    }))
    .filter((correction) => correction.userIssue && correction.correctAnswer)
    .filter((correction) => {
      const key = `${correction.userIssue}|${correction.correctAnswer}`.toLowerCase()
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
}

function compactValues(values: string[]) {
  return Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)))
}

function formatPlanScore(value: number) {
  return Number.isFinite(value) ? value.toFixed(2) : '-'
}

function formatContribution(value: number) {
  if (!Number.isFinite(value)) return '-'
  const formatted = Math.abs(value).toFixed(2)
  if (value > 0) return `+${formatted}`
  if (value < 0) return `-${formatted}`
  return '0.00'
}

function nextStatusLabel(status: string) {
  const labels: Record<string, string> = {
    due: '需要按期复验',
    first_pass: '初步掌握，仍需复验',
    long_term: '长期掌握',
    stable: '掌握稳定',
    unstable: '掌握不稳定',
  }
  return labels[status] ?? status
}

function reviewPlanText(status: string, direction: string, nextReviewAt: string | null) {
  const intervals: Record<string, string> = {
    due: '后续会按到期复验进入复习计划。',
    first_pass: '约 3 天后进入复习计划。',
    long_term: '约 30 天后进入长期复习计划。',
    stable: '约 14 天后进入巩固复习计划。',
    unstable: '明天优先进入复习计划。',
  }
  const date = formatNextReviewDate(nextReviewAt)
  const schedule = date ? `${date} 进入复习计划。` : (intervals[status] ?? '后续会按掌握情况进入复习计划。')
  const focus = direction.trim()
  return focus ? `${schedule}考察方向：${focus}` : schedule
}

function formatNextReviewDate(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })
}
