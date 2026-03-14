import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'

function StatusBadge({ status }) {
  const styles = {
    RUNNING:        'bg-green-500/15 text-green-400 border border-green-500/30',
    BUILDING:       'bg-yellow-500/15 text-yellow-400 border border-yellow-500/30',
    BUILD_COMPLETE: 'bg-blue-500/15 text-blue-400 border border-blue-500/30',
    CLONING:        'bg-blue-500/15 text-blue-400 border border-blue-500/30',
    STARTING:       'bg-blue-500/15 text-blue-400 border border-blue-500/30',
    QUEUED:         'bg-slate-500/15 text-slate-400 border border-slate-500/30',
    FAILED:         'bg-red-500/15 text-red-400 border border-red-500/30',
    STOPPED:        'bg-slate-500/15 text-slate-400 border border-slate-500/30',
    SUPERSEDED:     'bg-slate-500/15 text-slate-400 border border-slate-500/30',
  }
  return (
    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${styles[status] || styles.STOPPED}`}>
      {status}
    </span>
  )
}

function NewProjectModal({ onClose, onCreated }) {
  const [name, setName]       = useState('')
  const [repoUrl, setRepoUrl] = useState('')
  const [error, setError]     = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await api.post('/projects', { name, repoUrl })
      onCreated(res.data)
      onClose()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create project')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 px-4">
      <div className="bg-slate-900 border border-slate-700 rounded-xl w-full max-w-md p-6">
        <h2 className="text-white font-semibold text-lg mb-1">New Project</h2>
        <p className="text-slate-400 text-sm mb-5">Connect a GitHub repository to deploy</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-sm px-4 py-3 rounded-lg">
              {error}
            </div>
          )}
          <div>
            <label className="block text-sm text-slate-400 mb-1">Project name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full bg-slate-800 border border-slate-700 text-white text-sm rounded-lg px-3 py-2.5 focus:outline-none focus:border-blue-500"
              placeholder="My Awesome App"
            />
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">Repository URL</label>
            <input
              type="url"
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              required
              className="w-full bg-slate-800 border border-slate-700 text-white text-sm rounded-lg px-3 py-2.5 focus:outline-none focus:border-blue-500"
              placeholder="https://github.com/username/repo"
            />
            <p className="text-slate-500 text-xs mt-1">GitHub, GitLab, or Bitbucket only</p>
          </div>
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm py-2.5 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-800 text-white text-sm py-2.5 rounded-lg transition-colors"
            >
              {loading ? 'Creating...' : 'Create project'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function Dashboard() {
  const { user, logout }   = useAuth()
  const navigate           = useNavigate()
  const [projects, setProjects]       = useState([])
  const [loading, setLoading]         = useState(true)
  const [showModal, setShowModal]     = useState(false)
  const [actionLoading, setActionLoading] = useState({})

  const fetchProjects = async () => {
    try {
      const res = await api.get('/projects')
      setProjects(res.data)
    } catch (err) {
      console.error('Failed to fetch projects', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchProjects()
    const interval = setInterval(fetchProjects, 5000)
    return () => clearInterval(interval)
  }, [])

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const handleAction = async (projectId, action) => {
    setActionLoading((prev) => ({ ...prev, [`${projectId}-${action}`]: true }))
    try {
      await api.post(`/projects/${projectId}/deployments/${action}`)
      await fetchProjects()
    } catch (err) {
      alert(err.response?.data?.error || `${action} failed`)
    } finally {
      setActionLoading((prev) => ({ ...prev, [`${projectId}-${action}`]: false }))
    }
  }

  const handleDeploy = async (projectId) => {
    setActionLoading((prev) => ({ ...prev, [`${projectId}-deploy`]: true }))
    try {
      await api.post(`/projects/${projectId}/deployments`)
      await fetchProjects()
    } catch (err) {
      alert(err.response?.data?.error || 'Deploy failed')
    } finally {
      setActionLoading((prev) => ({ ...prev, [`${projectId}-deploy`]: false }))
    }
  }

  const handleDelete = async (projectId, projectName) => {
    if (!confirm(`Delete "${projectName}"? This will stop and remove all deployments.`)) return
    try {
      await api.delete(`/projects/${projectId}`)
      setProjects((prev) => prev.filter((p) => p.id !== projectId))
    } catch (err) {
      alert(err.response?.data?.error || 'Delete failed')
    }
  }

  const isActive = (status) =>
    ['RUNNING', 'BUILDING', 'CLONING', 'STARTING', 'BUILD_COMPLETE', 'QUEUED'].includes(status)

  const runningCount = projects.filter((p) => p.latestDeploymentStatus === 'RUNNING').length
  const failedCount  = projects.filter((p) => p.latestDeploymentStatus === 'FAILED').length

  return (
    <div className="min-h-screen">

      {/* Navbar */}
      <nav className="border-b border-slate-800 px-6 py-4 flex items-center justify-between">
        <h1 className="text-white font-semibold text-lg">AutoDeploy</h1>
        <div className="flex items-center gap-4">
          <span className="text-slate-400 text-sm hidden sm:block">{user?.email}</span>
          <button
            onClick={handleLogout}
            className="text-sm text-slate-400 hover:text-white transition-colors"
          >
            Logout
          </button>
        </div>
      </nav>

      <div className="max-w-5xl mx-auto px-6 py-8">

        {/* Stats */}
        <div className="grid grid-cols-3 gap-4 mb-8">
          <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-4">
            <p className="text-slate-400 text-xs mb-1">Total projects</p>
            <p className="text-white text-2xl font-semibold">{projects.length}</p>
          </div>
          <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-4">
            <p className="text-slate-400 text-xs mb-1">Running</p>
            <p className="text-green-400 text-2xl font-semibold">{runningCount}</p>
          </div>
          <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-4">
            <p className="text-slate-400 text-xs mb-1">Failed</p>
            <p className="text-red-400 text-2xl font-semibold">{failedCount}</p>
          </div>
        </div>

        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-white font-medium">Your projects</h2>
          <button
            onClick={() => setShowModal(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg transition-colors"
          >
            + New project
          </button>
        </div>

        {/* Projects list */}
        {loading ? (
          <div className="text-slate-400 text-sm py-12 text-center">Loading projects...</div>
        ) : projects.length === 0 ? (
          <div className="border border-dashed border-slate-700 rounded-xl py-16 text-center">
            <p className="text-slate-400 text-sm">No projects yet</p>
            <p className="text-slate-500 text-xs mt-1">Click "New project" to deploy your first app</p>
          </div>
        ) : (
          <div className="space-y-3">
            {projects.map((project) => (
              <div
                key={project.id}
                className="bg-slate-800/40 border border-slate-700 rounded-xl p-4"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <h3
                        className="text-white font-medium text-sm cursor-pointer hover:text-blue-400 transition-colors"
                        onClick={() => navigate(`/projects/${project.id}`)}
                      >
                        {project.name}
                      </h3>
                      <StatusBadge status={project.latestDeploymentStatus || 'STOPPED'} />
                    </div>
                    <p className="text-slate-500 text-xs truncate">{project.repoUrl}</p>
                    {project.publicUrl && project.latestDeploymentStatus === 'RUNNING' && (
                      <a
                        href={project.publicUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-blue-400 text-xs hover:underline mt-1 inline-block"
                      >
                        {project.publicUrl} ↗
                      </a>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {!isActive(project.latestDeploymentStatus) && (
                      <button
                        onClick={() => handleDeploy(project.id)}
                        disabled={actionLoading[`${project.id}-deploy`]}
                        className="text-xs bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 border border-blue-500/30 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
                      >
                        {actionLoading[`${project.id}-deploy`] ? '...' : 'Deploy'}
                      </button>
                    )}
                    {project.latestDeploymentStatus === 'RUNNING' && (
                      <>
                        <button
                          onClick={() => handleAction(project.id, 'restart')}
                          disabled={actionLoading[`${project.id}-restart`]}
                          className="text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
                        >
                          {actionLoading[`${project.id}-restart`] ? '...' : 'Restart'}
                        </button>
                        <button
                          onClick={() => handleAction(project.id, 'stop')}
                          disabled={actionLoading[`${project.id}-stop`]}
                          className="text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
                        >
                          {actionLoading[`${project.id}-stop`] ? '...' : 'Stop'}
                        </button>
                      </>
                    )}
                    {['STOPPED', 'FAILED', 'RUNNING'].includes(project.latestDeploymentStatus) && (
                      <button
                        onClick={() => handleAction(project.id, 'redeploy')}
                        disabled={actionLoading[`${project.id}-redeploy`]}
                        className="text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
                      >
                        {actionLoading[`${project.id}-redeploy`] ? '...' : 'Redeploy'}
                      </button>
                    )}
                    <button
                      onClick={() => navigate(`/projects/${project.id}`)}
                      className="text-xs bg-slate-700 hover:bg-slate-600 text-slate-300 px-3 py-1.5 rounded-lg transition-colors"
                    >
                      Logs
                    </button>
                    <button
                      onClick={() => handleDelete(project.id, project.name)}
                      className="text-xs bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 px-3 py-1.5 rounded-lg transition-colors"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showModal && (
        <NewProjectModal
          onClose={() => setShowModal(false)}
          onCreated={(newProject) => setProjects((prev) => [...prev, newProject])}
        />
      )}
    </div>
  )
}