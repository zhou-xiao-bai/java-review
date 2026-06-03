import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, Loader2, Play, Plus, Save, Trash2 } from 'lucide-react'

import {
  answerProjectSession,
  createProjectCase,
  deleteProjectCase,
  getApiErrorMessage,
  getProjectCases,
  startProjectSession,
  updateProjectCase,
  type ProjectCase,
  type ProjectCaseRequest,
  type ProjectSession,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const projectsQueryKey = ['project-cases'] as const

export function ProjectsPage() {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [session, setSession] = useState<ProjectSession | null>(null)
  const projectsQuery = useQuery({ queryKey: projectsQueryKey, queryFn: getProjectCases })
  const projects = projectsQuery.data ?? []
  const selected = projects.find((project) => project.id === selectedId) ?? projects[0] ?? null

  const createMutation = useMutation({
    mutationFn: createProjectCase,
    onSuccess: async (project) => {
      setSelectedId(project.id)
      await queryClient.invalidateQueries({ queryKey: projectsQueryKey })
    },
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ProjectCaseRequest }) => updateProjectCase(id, body),
    onSuccess: async (project) => {
      setSelectedId(project.id)
      await queryClient.invalidateQueries({ queryKey: projectsQueryKey })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deleteProjectCase,
    onSuccess: async () => {
      setSelectedId(null)
      await queryClient.invalidateQueries({ queryKey: projectsQueryKey })
    },
  })
  const startMutation = useMutation({ mutationFn: startProjectSession, onSuccess: setSession })
  const answerMutation = useMutation({
    mutationFn: ({ id, answer }: { id: string; answer: string }) => answerProjectSession(id, answer),
    onSuccess: async (nextSession) => {
      setSession(nextSession)
      await queryClient.invalidateQueries({ queryKey: projectsQueryKey })
    },
  })

  const errorMessage =
    getApiErrorMessage(createMutation.error, '') ||
    getApiErrorMessage(updateMutation.error, '') ||
    getApiErrorMessage(deleteMutation.error, '') ||
    getApiErrorMessage(startMutation.error, '') ||
    getApiErrorMessage(answerMutation.error, '') ||
    (projectsQuery.isError ? getApiErrorMessage(projectsQuery.error) : '')

  return (
    <section className="space-y-5">
      <div>
        <h1 className="text-3xl font-semibold text-slate-950">项目深挖</h1>
      </div>
      {errorMessage ? <ErrorBanner message={errorMessage} /> : null}

      <div className="grid gap-5 xl:grid-cols-[280px_minmax(0,1fr)_380px]">
        <aside className="h-fit rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-950">项目案例</div>
          <div className="divide-y divide-slate-100">
            {projects.map((project) => (
              <button key={project.id} className={cn('block w-full px-4 py-3 text-left hover:bg-slate-50', selected?.id === project.id ? 'bg-emerald-50' : '')} type="button" onClick={() => setSelectedId(project.id)}>
                <div className="truncate text-sm font-medium text-slate-950">{project.name}</div>
                <div className="mt-1 truncate text-xs text-slate-500">{project.techStack.join(' / ') || '未填写技术栈'}</div>
              </button>
            ))}
            {projects.length === 0 ? <div className="p-4 text-sm text-slate-500">暂无项目案例。</div> : null}
          </div>
        </aside>

        <ProjectForm
          key={selected?.id ?? 'new'}
          project={selected}
          saving={createMutation.isPending || updateMutation.isPending}
          deleting={deleteMutation.isPending}
          onCreate={(body) => createMutation.mutate(body)}
          onUpdate={(id, body) => updateMutation.mutate({ id, body })}
          onDelete={(id) => deleteMutation.mutate(id)}
        />

        <DeepDivePanel
          answerPending={answerMutation.isPending}
          project={selected}
          session={session}
          startPending={startMutation.isPending}
          onStart={(project) => startMutation.mutate(project.id)}
          onAnswer={(answer) => {
            if (session) {
              answerMutation.mutate({ id: session.id, answer })
            }
          }}
        />
      </div>
    </section>
  )
}

function ProjectForm({ project, saving, deleting, onCreate, onUpdate, onDelete }: { project: ProjectCase | null; saving: boolean; deleting: boolean; onCreate: (body: ProjectCaseRequest) => void; onUpdate: (id: string, body: ProjectCaseRequest) => void; onDelete: (id: string) => void }) {
  const [form, setForm] = useState<ProjectCaseRequest>(() => project ? toForm(project) : emptyForm())
	function submit(event: FormEvent<HTMLFormElement>) {
	  event.preventDefault()
	  if (project) {
	    onUpdate(project.id, form)
	  } else {
	    onCreate(form)
	  }
	}
  return (
    <form className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" onSubmit={submit}>
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-slate-950">{project ? '编辑项目案例' : '新增项目案例'}</h2>
        {project ? <button className="inline-flex h-9 items-center gap-2 rounded-md border border-rose-200 bg-white px-3 text-sm font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-60" disabled={deleting} type="button" onClick={() => onDelete(project.id)}><Trash2 className="size-4" />删除</button> : null}
      </div>
      <div className="mt-4 space-y-3">
        <Input label="项目名称" value={form.name} onChange={(value) => setForm({ ...form, name: value })} />
        <Textarea label="业务背景" value={form.background} onChange={(value) => setForm({ ...form, background: value })} />
        <Textarea label="我的职责" value={form.responsibility} onChange={(value) => setForm({ ...form, responsibility: value })} />
        <Input label="技术栈（逗号分隔）" value={form.techStack.join(', ')} onChange={(value) => setForm({ ...form, techStack: split(value) })} />
        <Textarea label="关键问题/亮点（每行一个）" value={form.highlights.join('\n')} onChange={(value) => setForm({ ...form, highlights: splitLines(value) })} />
      </div>
      <button className="mt-5 inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60" disabled={saving} type="submit">
        {saving ? <Loader2 className="size-4 animate-spin" /> : project ? <Save className="size-4" /> : <Plus className="size-4" />}
        {project ? '保存项目' : '新增项目'}
      </button>
    </form>
  )
}

function DeepDivePanel({ project, session, startPending, answerPending, onStart, onAnswer }: { project: ProjectCase | null; session: ProjectSession | null; startPending: boolean; answerPending: boolean; onStart: (project: ProjectCase) => void; onAnswer: (answer: string) => void }) {
  const [answer, setAnswer] = useState('')
  const score = session?.evaluation?.score
  return (
    <aside className="h-fit rounded-lg border border-slate-200 bg-white shadow-sm xl:sticky xl:top-24">
      <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-950">深挖会话</div>
      <div className="space-y-4 p-4">
        <button className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60" disabled={!project || startPending} type="button" onClick={() => project && onStart(project)}>
          {startPending ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
          开始深挖
        </button>
        {session?.turns.map((turn) => <div key={turn.id} className={cn('rounded-md px-3 py-2 text-sm leading-6', turn.role === 'user' ? 'bg-slate-100' : 'bg-emerald-50 text-emerald-900')}>{turn.content}</div>)}
        {session?.status === 'active' ? (
          <form className="space-y-2" onSubmit={(event) => { event.preventDefault(); onAnswer(answer); setAnswer('') }}>
            <textarea required className="min-h-28 w-full rounded-md border border-slate-300 p-3 text-sm outline-none focus:border-slate-500" value={answer} onChange={(event) => setAnswer(event.target.value)} />
            <button className="h-10 rounded-md bg-slate-900 px-3 text-sm font-medium text-white disabled:opacity-60" disabled={answerPending} type="submit">提交回答</button>
          </form>
        ) : null}
        {score ? (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <Chip label="业务表达" value={score.businessExpression} />
              <Chip label="技术方案" value={score.technicalDesign} />
              <Chip label="决策取舍" value={score.tradeoffDecision} />
              <Chip label="证据指标" value={score.metricsEvidence} />
              <Chip label="排查闭环" value={score.troubleshootingLoop} />
              <Chip label="抗压" value={score.interviewPressure} />
            </div>
            <div className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">建议加入知识复习范围：{session?.suggestedTopics.join(' / ')}</div>
          </div>
        ) : null}
      </div>
    </aside>
  )
}

function Input({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label className="block"><span className="text-sm font-medium text-slate-700">{label}</span><input required={label === '项目名称'} className="mt-2 h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500" value={value} onChange={(event) => onChange(event.target.value)} /></label>
}

function Textarea({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label className="block"><span className="text-sm font-medium text-slate-700">{label}</span><textarea className="mt-2 min-h-20 w-full rounded-md border border-slate-300 p-3 text-sm outline-none focus:border-slate-500" value={value} onChange={(event) => onChange(event.target.value)} /></label>
}

function Chip({ label, value }: { label: string; value: number }) {
  return <div className="rounded-md bg-slate-50 px-2 py-2"><div className="text-xs text-slate-500">{label}</div><div className="text-sm font-semibold text-slate-950">{value.toFixed(1)}</div></div>
}

function ErrorBanner({ message }: { message: string }) {
  return <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700"><AlertCircle className="mt-0.5 size-4 shrink-0" />{message}</div>
}

function emptyForm(): ProjectCaseRequest { return { name: '', background: '', responsibility: '', techStack: [], highlights: [] } }
function toForm(project: ProjectCase): ProjectCaseRequest { return { name: project.name, background: project.background ?? '', responsibility: project.responsibility ?? '', techStack: project.techStack, highlights: project.highlights } }
function split(value: string) { return value.split(',').map((item) => item.trim()).filter(Boolean) }
function splitLines(value: string) { return value.split('\n').map((item) => item.trim()).filter(Boolean) }
