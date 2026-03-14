import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import Navbar from '../components/Navbar'
import StatusBadge from '../components/StatusBadge'
import { projectService, deploymentService } from '../api/projectService'

export default function ProjectDetail() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [project, setProject]       = useState(null)
  const [latest, setLatest]         = useState(null)
  const [logs, setLogs]             = useState([])
  const [loading, setLoading]       = useState(true)
  const [actionLoading, setActionLoading] = useState(null)

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 3000)
    return () => clearInterval(interval)
  }, [id])

  const fetchData = async () => {
    try {
      const [projRes, latestRes] = await Promise.all([
        projectService.getById(id),
        deploymentService.getLatest(id),
      ])
      setProject(projRes.data)
      setLatest(latestRes.data)

      if (latestRes.data?.id) {
        const logsRes = await deploymentService.getLogs(id, latestRes.data.id)
        setLogs(logsRes.data)
      }
    } catch (err) {
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const handleAction = async (action) => {
    setActionLoading(action)
    try {
      if (action === 'stop')     await deploymentService.stop(id)
      if (action === 'restart')  await deploymentService.restart(id)
      if (action === 'redeploy') await deploymentService.redeploy(id)
      fetchData()
    } catch (err) {
      alert(err.response?.data?.error || `${action} failed`)
    } finally {
      setActionLoading(null)
    }
  }

  if (loading) return (
    <div className="min-h-screen bg-slate-950">
      <Navbar />
      <div className="flex items-center justify-center h-64 text-slate-500 text-sm">Loading...</div>
    </div>
  )

  return (
    <div className="min-h-screen bg-slate-950">
      <Navbar />
      <main className="max-w-4xl mx-auto px-6 py-8">

        {/* Back + title */}
        <div className="flex items-center gap-3 mb-6">
          <button
            onClick={() => navigate('/dashboard')}
            className="text-slate-400 hover:text-white text-sm transition-colors"
          >
            ← Back
          </button>
          <h1 className="text-white font-semibold">{project?.name}</h1>
          {latest && <StatusBadge status={latest.status} />}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">

          {/* Deployment info */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <h3 className="text-slate-400 text-xs font-medium uppercase tracking-wider mb-4">Deployment info</h3>
            {latest ? (
              <div className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-500">Status</span>
                  <StatusBadge status={latest.status} />
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Port</span>
                  <span className="text-white">{latest.hostPort || '—'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Image</span>
                  <span className="text-white font-mono text-xs">{latest.imageTag || '—'}</span>
                </div>
                {latest.publicUrl && (
                  <div className="flex justify-between">
                    <span className="text-slate-500">URL</span>
                    <a href={latest.publicUrl} target="_blank" rel="noreferrer"
                      className="text-blue-400 hover:text-blue-300">
                      {latest.publicUrl} ↗
                    </a>
                  </div>
                )}
              </div>
            ) : (
              <p className="text-slate-600 text-sm">No deployments yet</p>
            )}

            {/* Actions */}
            <div className="flex gap-2 flex-wrap mt-5 pt-4 border-t border-slate-800">
              {latest?.status === 'RUNNING' && (
                <>
                  <button onClick={() => handleAction('restart')} disabled={actionLoading === 'restart'}
                    className="text-xs border border-slate-700 text-slate-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50">
                    {actionLoading === 'restart' ? 'Restarting...' : 'Restart'}
                  </button>
                  <button onClick={() => handleAction('stop')} disabled={actionLoading === 'stop'}
                    className="text-xs border border-slate-700 text-slate-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50">
                    {actionLoading === 'stop' ? 'Stopping...' : 'Stop'}
                  </button>
                </>
              )}
              {['STOPPED', 'FAILED'].includes(latest?.status) && (
                <button onClick={() => handleAction('redeploy')} disabled={actionLoading === 'redeploy'}
                  className="text-xs bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50">
                  {actionLoading === 'redeploy' ? 'Redeploying...' : 'Redeploy'}
                </button>
              )}
            </div>
          </div>

          {/* Repo info */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <h3 className="text-slate-400 text-xs font-medium uppercase tracking-wider mb-4">Repository</h3>
            <p className="text-blue-400 text-sm break-all">{project?.repoUrl}</p>
            <div className="mt-4 space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-500">Project ID</span>
                <span className="text-white">#{project?.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Status</span>
                <span className="text-white">{project?.status || '—'}</span>
              </div>
            </div>
          </div>

        </div>

        {/* Logs */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-slate-400 text-xs font-medium uppercase tracking-wider">
              Deployment logs
            </h3>
            <span className="text-slate-600 text-xs">{logs.length} lines</span>
          </div>
          <div className="bg-slate-950 rounded-lg p-4 font-mono text-xs max-h-80 overflow-y-auto space-y-1">
            {logs.length === 0 ? (
              <p className="text-slate-600">No logs yet.</p>
            ) : (
              logs.map((log, i) => (
                <div key={i} className={
                  log.level === 'ERROR' ? 'text-red-400' :
                  log.level === 'WARN'  ? 'text-yellow-400' :
                  'text-slate-300'
                }>
                  <span className="text-slate-600 mr-2 select-none">{i + 1}</span>
                  {log.message}
                </div>
              ))
            )}
          </div>
        </div>

      </main>
    </div>
  )
}