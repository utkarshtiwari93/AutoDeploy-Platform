import { useState } from 'react'
import { projectService } from '../api/projectService'

export default function CreateProjectModal({ onClose, onCreated }) {
  const [name, setName]       = useState('')
  const [repoUrl, setRepoUrl] = useState('')
  const [error, setError]     = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await projectService.create({ name, repoUrl })
      onCreated(res.data)
      onClose()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create project.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 px-4">
      <div className="bg-slate-900 border border-slate-700 rounded-xl w-full max-w-md p-6">

        <div className="flex items-center justify-between mb-5">
          <h2 className="text-white font-semibold">New Project</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white text-xl leading-none">×</button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-sm px-3 py-2 rounded-lg">
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
              placeholder="My App"
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
            <p className="text-xs text-slate-500 mt-1">GitHub, GitLab, or Bitbucket only</p>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 text-sm text-slate-400 border border-slate-700 py-2.5 rounded-lg hover:border-slate-500 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 text-sm text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-800 py-2.5 rounded-lg transition-colors"
            >
              {loading ? 'Creating...' : 'Create project'}
            </button>
          </div>
        </form>

      </div>
    </div>
  )
}