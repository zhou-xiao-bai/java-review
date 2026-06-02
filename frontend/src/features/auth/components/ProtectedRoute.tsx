import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { ApiError } from '@/lib/api'
import { useCurrentUser } from '@/features/auth/hooks/useCurrentUser'

export function ProtectedRoute() {
  const location = useLocation()
  const currentUserQuery = useCurrentUser()

  if (currentUserQuery.isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 text-sm text-slate-600">
        Checking session...
      </div>
    )
  }

  if (
    currentUserQuery.error instanceof ApiError &&
    currentUserQuery.error.status === 401
  ) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (currentUserQuery.isError) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
        <div className="rounded-lg border border-rose-200 bg-white p-5 text-sm text-rose-700 shadow-sm">
          Failed to load the current session.
        </div>
      </div>
    )
  }

  return <Outlet />
}
