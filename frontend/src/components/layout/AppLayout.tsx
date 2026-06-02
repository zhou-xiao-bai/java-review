import {
  BookOpenCheck,
  CalendarDays,
  FolderKanban,
  Gauge,
  LogOut,
  Settings,
  ShieldCheck,
  Target,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router-dom'

import { getHealth } from '@/lib/api'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/today', label: 'Today', icon: CalendarDays },
  { to: '/scope', label: 'Scope', icon: Target },
  { to: '/review/session', label: 'Session', icon: BookOpenCheck },
  { to: '/projects', label: 'Projects', icon: FolderKanban },
  { to: '/progress', label: 'Progress', icon: Gauge },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function AppLayout() {
  const healthQuery = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    refetchInterval: 30_000,
  })

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-200 bg-white px-4 py-5 md:flex md:flex-col">
        <div className="flex items-center gap-3 px-2">
          <div className="flex size-10 items-center justify-center rounded-lg bg-emerald-600 text-white">
            <ShieldCheck className="size-5" aria-hidden="true" />
          </div>
          <div>
            <div className="text-sm font-semibold text-slate-950">
              Java Review
            </div>
            <div className="text-xs text-slate-500">Interview workbench</div>
          </div>
        </div>

        <nav className="mt-8 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition',
                  isActive
                    ? 'bg-slate-900 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950',
                )
              }
            >
              <item.icon className="size-4" aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-500">
          Backend:{' '}
          <span className="font-medium text-slate-800">
            {healthQuery.isSuccess ? healthQuery.data.status : 'checking'}
          </span>
        </div>
      </aside>

      <div className="md:pl-64">
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur md:px-8">
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="text-sm font-semibold text-slate-950">
                Review console
              </div>
              <div className="text-xs text-slate-500">
                {healthQuery.isError
                  ? 'Backend health check failed'
                  : 'Local MVP foundation'}
              </div>
            </div>
            <button
              type="button"
              className="inline-flex h-9 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50"
            >
              <LogOut className="size-4" aria-hidden="true" />
              Logout
            </button>
          </div>
        </header>

        <main className="px-4 py-8 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
