import { useQuery } from '@tanstack/react-query'

import { getHealth } from '@/lib/api'

export function TodayPage() {
  const healthQuery = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
  })

  return (
    <section className="space-y-6">
      <div>
        <div className="text-sm font-medium text-emerald-700">
          M0 foundation
        </div>
        <h1 className="mt-2 text-3xl font-semibold text-slate-950">
          Today Review
        </h1>
        <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
          The application shell is ready. This page will become the daily review
          plan workspace after topic scope and planning APIs are implemented.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="text-sm font-medium text-slate-500">
            Backend health
          </div>
          <div className="mt-3 text-2xl font-semibold text-slate-950">
            {healthQuery.isSuccess ? healthQuery.data.status : 'Checking'}
          </div>
          <div className="mt-1 text-xs text-slate-500">
            {healthQuery.isSuccess
              ? healthQuery.data.application
              : 'Calling /api/health through Vite proxy'}
          </div>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="text-sm font-medium text-slate-500">
            Default capacity
          </div>
          <div className="mt-3 text-2xl font-semibold text-slate-950">
            60 min
          </div>
          <div className="mt-1 text-xs text-slate-500">
            User preference arrives in M4/M6 settings.
          </div>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="text-sm font-medium text-slate-500">Routes</div>
          <div className="mt-3 text-2xl font-semibold text-slate-950">7</div>
          <div className="mt-1 text-xs text-slate-500">
            Login, today, scope, session, projects, progress, settings.
          </div>
        </div>
      </div>
    </section>
  )
}
