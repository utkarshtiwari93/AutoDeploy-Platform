import { useNavigate } from 'react-router-dom'

export default function NotFound() {
  const navigate = useNavigate()
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4">
      <p className="text-slate-500 text-7xl font-bold">404</p>
      <p className="text-white text-lg font-medium">Page not found</p>
      <p className="text-slate-400 text-sm">The page you're looking for doesn't exist.</p>
      <button
        onClick={() => navigate('/dashboard')}
        className="mt-2 bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg transition-colors"
      >
        Back to dashboard
      </button>
    </div>
  )
}