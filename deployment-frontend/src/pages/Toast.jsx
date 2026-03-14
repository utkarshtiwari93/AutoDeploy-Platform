import { useState, useEffect, createContext, useContext, useCallback } from 'react'

const ToastContext = createContext(null)

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const addToast = useCallback((message, type = 'info') => {
    const id = Date.now()
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id))
    }, 4000)
  }, [])

  const removeToast = (id) => setToasts(prev => prev.filter(t => t.id !== id))

  return (
    <ToastContext.Provider value={{ addToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 space-y-2">
        {toasts.map(toast => (
          <div
            key={toast.id}
            className={`flex items-center gap-3 px-4 py-3 rounded-lg border text-sm shadow-lg max-w-sm transition-all
              ${toast.type === 'success' ? 'bg-green-500/10 border-green-500/30 text-green-400' : ''}
              ${toast.type === 'error'   ? 'bg-red-500/10 border-red-500/30 text-red-400'     : ''}
              ${toast.type === 'info'    ? 'bg-blue-500/10 border-blue-500/30 text-blue-400'  : ''}
              ${toast.type === 'warning' ? 'bg-yellow-500/10 border-yellow-500/30 text-yellow-400' : ''}
            `}
          >
            <span className="flex-1">{toast.message}</span>
            <button
              onClick={() => removeToast(toast.id)}
              className="text-current opacity-60 hover:opacity-100 transition-opacity"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  return useContext(ToastContext)
}