type PagePlaceholderProps = {
  title: string
  description: string
  status?: string
}

export function PagePlaceholder({
  title,
  description,
  status = 'M0 placeholder',
}: PagePlaceholderProps) {
  return (
    <section className="space-y-6">
      <div>
        <div className="text-sm font-medium text-slate-500">{status}</div>
        <h1 className="mt-2 text-3xl font-semibold text-slate-950">{title}</h1>
        <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
          {description}
        </p>
      </div>
      <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-sm text-slate-500">
        This route is wired for the next milestone implementation.
      </div>
    </section>
  )
}
