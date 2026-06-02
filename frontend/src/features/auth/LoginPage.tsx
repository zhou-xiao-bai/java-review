export function LoginPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <section className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-slate-950">
            Java Review
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Sign in will be implemented in M1.
          </p>
        </div>
        <div className="space-y-3">
          <input
            disabled
            className="h-10 w-full rounded-md border border-slate-200 bg-slate-50 px-3 text-sm"
            placeholder="Username or email"
          />
          <input
            disabled
            className="h-10 w-full rounded-md border border-slate-200 bg-slate-50 px-3 text-sm"
            placeholder="Password"
            type="password"
          />
          <button
            disabled
            type="button"
            className="h-10 w-full rounded-md bg-slate-900 text-sm font-medium text-white opacity-60"
          >
            Sign in
          </button>
        </div>
      </section>
    </main>
  )
}
