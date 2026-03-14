import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
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

function LogLine({ text }) {
  let color = 'text-slate-300'
  if (text.includes('[INFO]'))    color = 'text-slate-300'
  if (text.includes('[ERROR]'))   color = 'text-red-400'
  if (text.includes('[WARN]'))    color = 'text-yellow-400'
  if (text.includes('successfully') || text.includes('Successfully') || text.includes('RUNNING'))
    color = 'text-green-400'
  if (text.includes('Step') && text.includes('/'))
    color = 'text-blue-400'
  if (text.includes('FROM') || text.includes('COPY') || text.includes('RUN') ||
      text.includes('WORKDIR') || text.includes('EXPOSE') || text.includes('CMD'))
    color = 'text-purple-400'

  return (
    <div className={`font-mono text-xs leading-5 ${color}`}>
      {text}
    </div>
  )
}

export default function LogViewer() {
  const { id, deploymentId } = useParams()
  const navigate             = useNavigate()

  const [logs, setLogs]           = useState([])
  const [status, setStatus]       = useState('CONNECTING')
  const [connected, setConnected] = useState(false)
  const [autoScroll, setAutoScroll] = useState(true)
  const [deployment, setDeployment] = useState(null)

  const logEndRef    = useRef(null)
  const logBoxRef    = useRef(null)
  const eventSrcRef  = useRef(null)
  const lastIdRef    = useRef(0)

  // Fetch deployment info
  useEffect(() => {
    api.get(`/projects/${id}/deployments`)
      .then(res => {
        const dep = res.data.find(d => d.id === parseInt(deploymentId))
        if (dep) setDeployment(dep)
      })
      .catch(console.error)
  }, [id, deploymentId])

  // Auto scroll
  useEffect(() => {
    if (autoScroll && logEndRef.current) {
      logEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [logs, autoScroll])

  // Detect manual scroll up → disable auto scroll
  const handleScroll = () => {
    const box = logBoxRef.current
    if (!box) return
    const isAtBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 50
    setAutoScroll(isAtBottom)
  }

  // SSE connection
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) return

    const connect = () => {
      const url = `http://localhost:8080/api/projects/${id}/deployments/${deploymentId}/logs/stream`

      // EventSource doesn't support headers natively
      // We use a workaround via query param or fetch — here we use fetch + ReadableStream
      const ctrl = new AbortController()

      fetch(url, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        },
        signal: ctrl.signal,
      }).then(async (response) => {
        if (!response.ok) {
          setStatus('FAILED')
          setConnected(false)
          return
        }

        setConnected(true)
        const reader  = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer    = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            setConnected(false)
            break
          }

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop()

          let eventType = 'message'
          let eventData = ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.replace('event:', '').trim()
            } else if (line.startsWith('data:')) {
              eventData = line.replace('data:', '').trim()
            } else if (line.startsWith('id:')) {
              lastIdRef.current = parseInt(line.replace('id:', '').trim())
            } else if (line === '') {
              // dispatch event
              if (eventData) {
                if (eventType === 'log') {
                  setLogs(prev => [...prev, eventData])
                } else if (eventType === 'status') {
                  setStatus(eventData)
                  if (['RUNNING', 'FAILED', 'STOPPED', 'SUPERSEDED'].includes(eventData)) {
                    setConnected(false)
                  }
                } else if (eventType === 'error') {
                  setLogs(prev => [...prev, `[ERROR] ${eventData}`])
                }
              }
              eventType = 'message'
              eventData = ''
            }
          }
        }
      }).catch((err) => {
        if (err.name !== 'AbortError') {
          setConnected(false)
          setLogs(prev => [...prev, '[ERROR] Connection lost'])
        }
      })

      return ctrl
    }

    const ctrl = connect()
    return () => ctrl?.abort()
  }, [id, deploymentId])

  const isTerminal = ['RUNNING', 'FAILED', 'STOPPED', 'SUPERSEDED'].includes(status)

  return (
    <div className="min-h-screen flex flex-col">

      {/* Navbar */}
      <nav className="border-b border-slate-800 px-6 py-4 flex items-center gap-4 flex-shrink-0">
        <button
          onClick={() => navigate(`/projects/${id}`)}
          className="text-slate-400 hover:text-white text-sm transition-colors"
        >
          ← Back
        </button>
        <h1 className="text-white font-semibold">
          Deployment #{deploymentId} logs
        </h1>
        <StatusBadge status={status} />
        {connected && (
          <span className="flex items-center gap-1.5 text-xs text-green-400">
            <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse inline-block" />
            Live
          </span>
        )}
        {!connected && isTerminal && (
          <span className="text-xs text-slate-500">Stream ended</span>
        )}
      </nav>

      {/* Stats bar */}
      <div className="border-b border-slate-800 px-6 py-2 flex items-center gap-6 text-xs text-slate-500 flex-shrink-0">
        <span>{logs.length} lines</span>
        {deployment && (
          <>
            <span>Image: <span className="text-slate-400 font-mono">{deployment.imageTag || '—'}</span></span>
            <span>Port: <span className="text-slate-400">{deployment.hostPort || '—'}</span></span>
          </>
        )}
        <button
          onClick={() => setAutoScroll(true)}
          className={`ml-auto text-xs px-2 py-0.5 rounded border transition-colors ${
            autoScroll
              ? 'bg-blue-600/20 text-blue-400 border-blue-500/30'
              : 'bg-slate-800 text-slate-400 border-slate-700 hover:text-white'
          }`}
        >
          Auto-scroll {autoScroll ? 'on' : 'off'}
        </button>
      </div>

      {/* Log box */}
      <div
        ref={logBoxRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto bg-slate-950 px-6 py-4"
        style={{ minHeight: '70vh' }}
      >
        {logs.length === 0 && !connected && status === 'CONNECTING' && (
          <p className="text-slate-500 text-xs font-mono">Connecting to log stream...</p>
        )}
        {logs.length === 0 && connected && (
          <p className="text-slate-500 text-xs font-mono animate-pulse">Waiting for logs...</p>
        )}

        {logs.map((line, i) => (
          <LogLine key={i} text={line} />
        ))}

        {connected && (
          <span className="inline-block w-2 h-3 bg-slate-400 animate-pulse ml-0.5 align-middle" />
        )}

        {isTerminal && status === 'RUNNING' && (
          <div className="mt-4 pt-4 border-t border-slate-800">
            <p className="text-green-400 text-xs font-mono">
              ✓ Deployment successful — app is live at{' '}
              {deployment?.publicUrl ? (
                <a href={deployment.publicUrl} target="_blank" rel="noreferrer"
                  className="underline hover:text-green-300">
                  {deployment.publicUrl}
                </a>
              ) : `port ${deployment?.hostPort}`}
            </p>
          </div>
        )}

        {isTerminal && status === 'FAILED' && (
          <div className="mt-4 pt-4 border-t border-slate-800">
            <p className="text-red-400 text-xs font-mono">✗ Deployment failed</p>
          </div>
        )}

        <div ref={logEndRef} />
      </div>

    </div>
  )
}