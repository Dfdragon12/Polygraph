import { useState, useEffect } from 'react'

export default function OfflineIndicator() {
  const [online, setOnline] = useState(navigator.onLine)
  const [mostrando, setMostrando] = useState(false)

  useEffect(() => {
    const alOnline = () => {
      setOnline(true)
      // Muestra brevemente "Conexión restaurada"
      setMostrando(true)
      setTimeout(() => setMostrando(false), 3000)
    }
    const alOffline = () => { setOnline(false); setMostrando(true) }

    window.addEventListener('online', alOnline)
    window.addEventListener('offline', alOffline)
    return () => {
      window.removeEventListener('online', alOnline)
      window.removeEventListener('offline', alOffline)
    }
  }, [])

  if (!mostrando && online) return null

  return (
    <div
      className={`fixed top-0 left-0 right-0 z-[200] py-2 px-4 flex items-center justify-center gap-2 text-sm font-medium transition-all ${
        online
          ? 'bg-green-500 text-white'
          : 'bg-amber-500 text-white'
      }`}
      style={{ paddingTop: 'calc(0.5rem + var(--sat))' }}
    >
      {online ? (
        <>
          <svg className="h-4 w-4 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
          Conexión restaurada
        </>
      ) : (
        <>
          <svg className="h-4 w-4 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 5.636a9 9 0 010 12.728M15.536 8.464a5 5 0 010 7.072M6.343 17.657A9 9 0 015.636 5.636m3.122 9.122a5 5 0 010-7.072" />
          </svg>
          Sin conexión — usando datos guardados
        </>
      )}
    </div>
  )
}
