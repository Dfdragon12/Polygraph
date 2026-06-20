import { useState, useEffect } from 'react'

const STORAGE_KEY = 'pwa-install-dismissed'

export default function PWAInstallBanner() {
  const [prompt, setPrompt] = useState(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    // No mostrar si ya está instalado o ya fue descartado
    const yaDescartado = sessionStorage.getItem(STORAGE_KEY)
    const yaInstalado  = window.matchMedia('(display-mode: standalone)').matches
    if (yaDescartado || yaInstalado) return

    const handler = (e) => {
      e.preventDefault()
      setPrompt(e)
      setVisible(true)
    }
    window.addEventListener('beforeinstallprompt', handler)
    return () => window.removeEventListener('beforeinstallprompt', handler)
  }, [])

  const instalar = async () => {
    if (!prompt) return
    prompt.prompt()
    const { outcome } = await prompt.userChoice
    if (outcome === 'accepted') setVisible(false)
    setPrompt(null)
  }

  const descartar = () => {
    sessionStorage.setItem(STORAGE_KEY, '1')
    setVisible(false)
  }

  if (!visible) return null

  return (
    <div
      className="fixed bottom-0 left-0 right-0 z-[200] bg-slate-900 text-white shadow-2xl slide-up"
      style={{ paddingBottom: 'calc(1rem + var(--sab))' }}
    >
      <div className="flex items-center gap-3 px-4 pt-4 pb-1">
        <img src="/icon.svg" alt="Polygraph" className="w-12 h-12 rounded-2xl flex-shrink-0 bg-indigo-600 p-1" />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold leading-tight">Instalar Polygraph ERP</p>
          <p className="text-xs text-slate-400 mt-0.5">Acceso rápido desde tu pantalla de inicio, sin navegador</p>
        </div>
        <button onClick={descartar} className="text-slate-500 hover:text-slate-300 p-1 flex-shrink-0" aria-label="Cerrar">
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
      <div className="flex gap-2 px-4 pb-2 mt-3">
        <button onClick={descartar}
          className="flex-1 py-2.5 text-sm text-slate-400 hover:text-white border border-slate-700 rounded-xl transition-colors">
          Ahora no
        </button>
        <button onClick={instalar}
          className="flex-1 py-2.5 text-sm font-semibold bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl transition-colors">
          Instalar app
        </button>
      </div>
    </div>
  )
}
