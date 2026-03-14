import { useAuth } from '../context/AuthContext'
import { useNavigate } from 'react-router-dom'

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <nav className="border-b border-slate-800 px-6 py-3 flex items-center justify-between sticky top-0 bg-slate-950 z-10">
      <div className="flex items-center gap-2">
        <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center">
          <span className="text-white text-xs font-bold">AD</span>
        </div>
        <span className="text-white font-semibold text-sm">AutoDeploy</span>
      </div>
      <div className="flex items-center gap-4">
        <span className="text-slate-400 text-sm hidden sm:block">{user?.email}</span>
        <button
          onClick={handleLogout}
          className="text-sm text-slate-400 hover:text-white border border-slate-700 hover:border-slate-500 px-3 py-1.5 rounded-lg transition-colors"
        >
          Logout
        </button>
      </div>
    </nav>
  )
}