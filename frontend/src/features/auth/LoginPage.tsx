import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import {
  bootstrapAdmin,
  getApiErrorMessage,
  getBootstrapStatus,
  login,
} from '@/lib/api'
import {
  currentUserQueryKey,
  useCurrentUser,
} from '@/features/auth/hooks/useCurrentUser'

type LocationState = {
  from?: {
    pathname?: string
  }
}

export function LoginPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as LocationState | null
  const redirectTo = locationState?.from?.pathname || '/today'

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(true)

  const currentUserQuery = useCurrentUser()
  const bootstrapStatusQuery = useQuery({
    queryKey: ['auth', 'bootstrap-status'],
    queryFn: getBootstrapStatus,
  })

  const initialized = bootstrapStatusQuery.data?.initialized ?? true

  const bootstrapMutation = useMutation({
    mutationFn: bootstrapAdmin,
    onSuccess: async () => {
      await bootstrapStatusQuery.refetch()
      setIdentifier(username)
      setPassword('')
    },
  })

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: async (user) => {
      queryClient.setQueryData(currentUserQueryKey, user)
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey })
      navigate(redirectTo, { replace: true })
    },
  })

  if (currentUserQuery.isSuccess) {
    return <Navigate to={redirectTo} replace />
  }

  const loading =
    bootstrapStatusQuery.isLoading ||
    bootstrapMutation.isPending ||
    loginMutation.isPending

  const errorMessage =
    getApiErrorMessage(bootstrapMutation.error, '') ||
    getApiErrorMessage(loginMutation.error, '') ||
    (bootstrapStatusQuery.isError
      ? getApiErrorMessage(bootstrapStatusQuery.error)
      : '')

  function handleBootstrapSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    bootstrapMutation.mutate({
      username,
      email: email || undefined,
      displayName,
      password,
    })
  }

  function handleLoginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    loginMutation.mutate({
      identifier,
      password,
      rememberMe,
    })
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10">
      <section className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-6">
          <div className="text-sm font-medium text-emerald-700">
            Java Review
          </div>
          <h1 className="mt-2 text-2xl font-semibold text-slate-950">
            {initialized ? '登录' : '创建管理员'}
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            {initialized
              ? '使用管理员账号进入复习工作台。'
              : '为当前实例创建第一个本地管理员账号。'}
          </p>
        </div>

        {errorMessage ? (
          <div className="mb-4 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
            {errorMessage}
          </div>
        ) : null}

        {initialized ? (
          <form className="space-y-4" onSubmit={handleLoginSubmit}>
            <label className="block text-sm font-medium text-slate-700">
              用户名或邮箱
              <input
                required
                className="mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
              />
            </label>
            <label className="block text-sm font-medium text-slate-700">
              密码
              <input
                required
                className="mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                minLength={8}
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input
                className="size-4 rounded border-slate-300"
                type="checkbox"
                checked={rememberMe}
                onChange={(event) => setRememberMe(event.target.checked)}
              />
              记住此浏览器
            </label>
            <button
              disabled={loading}
              type="submit"
              className="h-10 w-full rounded-md bg-slate-900 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loginMutation.isPending ? '登录中...' : '登录'}
            </button>
          </form>
        ) : (
          <form className="space-y-4" onSubmit={handleBootstrapSubmit}>
            <label className="block text-sm font-medium text-slate-700">
              用户名
              <input
                required
                className="mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                minLength={3}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>
            <label className="block text-sm font-medium text-slate-700">
              邮箱
              <input
                className="mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            <label className="block text-sm font-medium text-slate-700">
              显示名称
              <input
                required
                className="mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </label>
            <label className="block text-sm font-medium text-slate-700">
              密码
              <input
                required
                className="mt-1 h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-500"
                minLength={8}
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            <button
              disabled={loading}
              type="submit"
              className="h-10 w-full rounded-md bg-slate-900 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {bootstrapMutation.isPending
                ? '创建中...'
                : '创建管理员'}
            </button>
          </form>
        )}
      </section>
    </main>
  )
}
