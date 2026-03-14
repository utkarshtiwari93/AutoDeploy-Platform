export default function StatusBadge({ status }) {
  const styles = {
    RUNNING:        'bg-green-500/15 text-green-400 border-green-500/30',
    BUILDING:       'bg-yellow-500/15 text-yellow-400 border-yellow-500/30',
    BUILD_COMPLETE: 'bg-blue-500/15 text-blue-400 border-blue-500/30',
    STARTING:       'bg-blue-500/15 text-blue-400 border-blue-500/30',
    CLONING:        'bg-purple-500/15 text-purple-400 border-purple-500/30',
    QUEUED:         'bg-slate-500/15 text-slate-400 border-slate-500/30',
    STOPPED:        'bg-slate-500/15 text-slate-400 border-slate-500/30',
    FAILED:         'bg-red-500/15 text-red-400 border-red-500/30',
    SUPERSEDED:     'bg-slate-500/15 text-slate-500 border-slate-600/30',
  }

  const dots = {
    RUNNING:  'bg-green-400 animate-pulse',
    BUILDING: 'bg-yellow-400 animate-pulse',
    STARTING: 'bg-blue-400 animate-pulse',
    CLONING:  'bg-purple-400 animate-pulse',
    QUEUED:   'bg-slate-400 animate-pulse',
    FAILED:   'bg-red-400',
    STOPPED:  'bg-slate-500',
  }

  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${styles[status] || styles.STOPPED}`}>
      {dots[status] && (
        <span className={`w-1.5 h-1.5 rounded-full ${dots[status]}`} />
      )}
      {status}
    </span>
  )
}