import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, Loader2, LogOut, PlugZap, Plus, Save, Trash2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import {
  currentUserQueryKey,
  useCurrentUser,
} from '@/features/auth/hooks/useCurrentUser'
import {
  getApiErrorMessage,
  getSettings,
  logout,
  testLlmSettings,
  updateSettings,
  type LlmConfigRequest,
  type UpdateSettingsRequest,
} from '@/lib/api'
import { cn } from '@/lib/utils'

const settingsQueryKey = ['settings'] as const

export function SettingsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUserQuery = useCurrentUser()
  const settingsQuery = useQuery({ queryKey: settingsQueryKey, queryFn: getSettings })

  const saveMutation = useMutation({
    mutationFn: updateSettings,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: settingsQueryKey })
    },
  })
  const testMutation = useMutation({ mutationFn: testLlmSettings })
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: async () => {
      queryClient.removeQueries({ queryKey: currentUserQueryKey })
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey })
      navigate('/login', { replace: true })
    },
  })

  const errorMessage =
    getApiErrorMessage(saveMutation.error, '') ||
    getApiErrorMessage(testMutation.error, '') ||
    (settingsQuery.isError ? getApiErrorMessage(settingsQuery.error) : '')

  const initialForm: UpdateSettingsRequest = settingsQuery.data
    ? {
        activeLlmConfigId: settingsQuery.data.activeLlmConfigId,
        llmConfigs: settingsQuery.data.llmConfigs.map((config) => ({
          id: config.id,
          name: config.name,
          provider: config.provider,
          baseUrl: config.baseUrl ?? '',
          apiKey: config.apiKeyConfigured ? '********' : '',
          model: config.model,
        })),
        requestTimeoutSeconds: settingsQuery.data.requestTimeoutSeconds,
        dailyReviewMinutes: settingsQuery.data.dailyReviewMinutes,
        reviewedPointSchedulingPolicy:
          settingsQuery.data.reviewedPointSchedulingPolicy ?? 'follow_scope',
      }
    : {
        activeLlmConfigId: 'default',
        llmConfigs: [defaultConfig('default')],
        requestTimeoutSeconds: 30,
        dailyReviewMinutes: 60,
        reviewedPointSchedulingPolicy: 'follow_scope',
      }

  return (
    <section className="space-y-5">
      <div>
        <h1 className="text-3xl font-semibold text-slate-950">设置</h1>
      </div>

      {errorMessage ? (
        <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <SettingsForm
          key={
            settingsQuery.data
              ? `${settingsQuery.data.activeLlmConfigId}-${settingsQuery.data.llmConfigs.length}-${settingsQuery.data.reviewedPointSchedulingPolicy}`
              : 'loading'
          }
          form={initialForm}
          saving={saveMutation.isPending}
          saveSuccess={saveMutation.isSuccess}
          testPending={testMutation.isPending}
          testMessage={testMutation.data?.message}
          onSave={(nextForm) => saveMutation.mutate(nextForm)}
          onTest={() => testMutation.mutate()}
        />

        <aside className="h-fit rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-950">账号</h2>
          <div className="mt-4 space-y-3 text-sm">
            <Info label="用户名" value={currentUserQuery.data?.username ?? '-'} />
            <Info label="邮箱" value={currentUserQuery.data?.email ?? '-'} />
            <Info label="角色" value={currentUserQuery.data?.role ?? '-'} />
          </div>
          <button
            className="mt-5 inline-flex h-10 w-full items-center justify-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
            disabled={logoutMutation.isPending}
            type="button"
            onClick={() => logoutMutation.mutate()}
          >
            <LogOut className="size-4" />
            退出登录
          </button>
        </aside>
      </div>
    </section>
  )
}

function SettingsForm({
  form: initialForm,
  saving,
  saveSuccess,
  testPending,
  testMessage,
  onSave,
  onTest,
}: {
  form: UpdateSettingsRequest
  saving: boolean
  saveSuccess: boolean
  testPending: boolean
  testMessage?: string
  onSave: (form: UpdateSettingsRequest) => void
  onTest: () => void
}) {
  const [form, setForm] = useState(initialForm)
  const activeConfig = form.llmConfigs.find((config) => config.id === form.activeLlmConfigId) ?? form.llmConfigs[0]

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSave(form)
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-base font-semibold text-slate-950">LLM 中转站</h2>
              <button className="inline-flex h-9 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50" type="button" onClick={() => setForm(addConfig(form))}>
                <Plus className="size-4" />
                新增
              </button>
            </div>
            <div className="mt-4 grid gap-4 lg:grid-cols-[220px_minmax(0,1fr)]">
              <div className="space-y-2">
                {form.llmConfigs.map((config) => (
                  <button key={config.id} className={cn('block w-full rounded-md border px-3 py-2 text-left text-sm', config.id === form.activeLlmConfigId ? 'border-emerald-200 bg-emerald-50 text-emerald-900' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50')} type="button" onClick={() => setForm({ ...form, activeLlmConfigId: config.id })}>
                    <div className="truncate font-medium">{config.name}</div>
                    <div className="mt-1 truncate text-xs opacity-70">{config.model}</div>
                  </button>
                ))}
              </div>
              {activeConfig ? (
                <div className="grid gap-4 md:grid-cols-2">
                  <Field label="名称">
                    <input required className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500" value={activeConfig.name} onChange={(event) => setForm(updateConfig(form, activeConfig.id, { name: event.target.value }))} />
                  </Field>
                  <Field label="Provider">
                    <select className="h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500" value={activeConfig.provider} onChange={(event) => setForm(updateConfig(form, activeConfig.id, { provider: event.target.value }))}>
                      <option value="openai-compatible">OpenAI Compatible</option>
                    </select>
                  </Field>
                  <Field label="模型名">
                    <input required className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500" value={activeConfig.model} onChange={(event) => setForm(updateConfig(form, activeConfig.id, { model: event.target.value }))} />
                  </Field>
                  <Field label="Base URL">
                    <input className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500" value={activeConfig.baseUrl} onChange={(event) => setForm(updateConfig(form, activeConfig.id, { baseUrl: event.target.value }))} />
                  </Field>
                  <Field label="API Key">
                    <input className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500" type="password" value={activeConfig.apiKey ?? ''} onChange={(event) => setForm(updateConfig(form, activeConfig.id, { apiKey: event.target.value }))} />
                  </Field>
                  <div className="flex items-end">
                    <button className="inline-flex h-10 items-center gap-2 rounded-md border border-rose-200 bg-white px-3 text-sm font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-60" disabled={form.llmConfigs.length <= 1} type="button" onClick={() => setForm(removeConfig(form, activeConfig.id))}>
                      <Trash2 className="size-4" />
                      删除当前中转站
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <Field label="请求超时（秒）">
                <input
                  min={1}
                  max={300}
                  className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500"
                  type="number"
                  value={form.requestTimeoutSeconds}
                  onChange={(event) =>
                    setForm({ ...form, requestTimeoutSeconds: Number(event.target.value) })
                  }
                />
              </Field>
              <Field label="每日复习时长（分钟）">
                <input
                  min={10}
                  max={240}
                  className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500"
                  type="number"
                  value={form.dailyReviewMinutes}
                  onChange={(event) =>
                    setForm({ ...form, dailyReviewMinutes: Number(event.target.value) })
                  }
                />
              </Field>
            </div>
            <div className="mt-4">
              <Field label="已复习题目排期">
                <div className="grid gap-2 sm:grid-cols-2">
                  <button
                    type="button"
                    onClick={() =>
                      setForm({
                        ...form,
                        reviewedPointSchedulingPolicy: 'follow_scope',
                      })
                    }
                    className={cn(
                      'rounded-md border px-3 py-2 text-left text-sm',
                      form.reviewedPointSchedulingPolicy === 'follow_scope'
                        ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                    )}
                  >
                    <span className="block font-medium">跟随范围</span>
                    <span className="mt-1 block text-xs opacity-70">
                      到期和顺延题仍受当前范围控制
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      setForm({
                        ...form,
                        reviewedPointSchedulingPolicy: 'keep_reviewed',
                      })
                    }
                    className={cn(
                      'rounded-md border px-3 py-2 text-left text-sm',
                      form.reviewedPointSchedulingPolicy === 'keep_reviewed'
                        ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                    )}
                  >
                    <span className="block font-medium">复习后独立</span>
                    <span className="mt-1 block text-xs opacity-70">
                      已复习题到期后继续进入计划
                    </span>
                  </button>
                </div>
              </Field>
            </div>
            <div className="mt-5 flex flex-wrap gap-2">
              <button
                className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
                disabled={saving}
                type="submit"
              >
                {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                保存设置
              </button>
              <button
                className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                disabled={testPending}
                type="button"
                onClick={onTest}
              >
                {testPending ? <Loader2 className="size-4 animate-spin" /> : <PlugZap className="size-4" />}
                测试连接
              </button>
            </div>
            {saveSuccess ? <InlineStatus text="设置已保存" /> : null}
            {testMessage ? <InlineStatus text={testMessage} /> : null}
          </section>
        </form>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <span className="mt-2 block">{children}</span>
    </label>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-slate-100 pb-2">
      <span className="text-slate-500">{label}</span>
      <span className="truncate font-medium text-slate-900">{value}</span>
    </div>
  )
}

function InlineStatus({ text }: { text: string }) {
  return (
    <div className="mt-3 flex items-center gap-2 text-sm text-emerald-700">
      <CheckCircle2 className="size-4" />
      {text}
    </div>
  )
}

function defaultConfig(id = `config-${Date.now()}`): LlmConfigRequest {
  return {
    id,
    name: '默认中转站',
    provider: 'openai-compatible',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    model: 'gpt-4o-mini',
  }
}

function addConfig(form: UpdateSettingsRequest): UpdateSettingsRequest {
  const config = { ...defaultConfig(), name: `中转站 ${form.llmConfigs.length + 1}` }
  return { ...form, activeLlmConfigId: config.id, llmConfigs: [...form.llmConfigs, config] }
}

function updateConfig(form: UpdateSettingsRequest, id: string, patch: Partial<LlmConfigRequest>): UpdateSettingsRequest {
  return {
    ...form,
    llmConfigs: form.llmConfigs.map((config) => (config.id === id ? { ...config, ...patch } : config)),
  }
}

function removeConfig(form: UpdateSettingsRequest, id: string): UpdateSettingsRequest {
  const nextConfigs = form.llmConfigs.filter((config) => config.id !== id)
  const activeLlmConfigId = form.activeLlmConfigId === id ? nextConfigs[0]?.id ?? form.activeLlmConfigId : form.activeLlmConfigId
  return { ...form, activeLlmConfigId, llmConfigs: nextConfigs }
}
