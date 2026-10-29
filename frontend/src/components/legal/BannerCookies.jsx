import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { guardarConsentimiento, leerConsentimiento } from '../../utils/consentimiento'

function Interruptor({ id, titulo, texto, activo, onChange, fijo }) {
  return (
    <div className="flex items-start gap-3 py-2">
      <input
        id={id} type="checkbox" checked={activo} disabled={fijo}
        onChange={(e) => onChange?.(e.target.checked)}
        className="mt-1 h-5 w-5 accent-primary-600"
      />
      <label htmlFor={id} className="text-sm text-gray-700">
        <span className="font-semibold text-gray-900">{titulo}{fijo ? ' (siempre activas)' : ''}</span>
        <span className="block text-gray-600">{texto}</span>
      </label>
    </div>
  )
}

/** Aviso de cookies: Aceptar y Rechazar pesan lo mismo; no hay casillas premarcadas. */
export default function BannerCookies() {
  const [visible, setVisible] = useState(() => leerConsentimiento() === null)
  const [configurando, setConfigurando] = useState(false)
  const [analiticas, setAnaliticas] = useState(false)
  const [marketing, setMarketing] = useState(false)
  const caja = useRef(null)

  useEffect(() => {
    const abrir = () => {
      const c = leerConsentimiento()
      setAnaliticas(!!c?.analiticas); setMarketing(!!c?.marketing)
      setConfigurando(true); setVisible(true)
    }
    window.addEventListener('abrir-preferencias-cookies', abrir)
    return () => window.removeEventListener('abrir-preferencias-cookies', abrir)
  }, [])

  useEffect(() => { if (visible) caja.current?.focus() }, [visible, configurando])

  if (!visible) return null

  const decidir = (a, m) => { guardarConsentimiento({ analiticas: a, marketing: m }); setVisible(false); setConfigurando(false) }
  const btn = 'min-h-[44px] px-4 rounded-xl text-sm font-semibold border border-gray-400 bg-white text-gray-900 hover:bg-gray-100'

  return (
    <div
      ref={caja} tabIndex={-1} role="dialog" aria-modal="false" aria-labelledby="cookies-titulo"
      className="fixed bottom-0 inset-x-0 z-[60] p-3 md:p-4 pb-[calc(0.75rem+var(--sab))]"
    >
      <div className="mx-auto max-w-3xl rounded-2xl border border-gray-300 bg-white p-4 shadow-2xl">
        <h2 id="cookies-titulo" className="text-base font-bold text-gray-900">Su privacidad y las cookies</h2>
        <p className="mt-1 text-sm text-gray-700">
          Usamos cookies técnicas para que la plataforma funcione. Las analíticas y de marketing solo se activan si usted
          las acepta. Rechazarlas no afecta el servicio. Más información en la{' '}
          <Link to="/legal/cookies" className="text-primary-700 underline">Política de Cookies</Link>.
        </p>
        {configurando && (
          <div className="mt-3 divide-y divide-gray-200 border-t border-gray-200">
            <Interruptor id="ck-tec" titulo="Técnicas" fijo activo texto="Sesión, seguridad y recordar su elección." />
            <Interruptor id="ck-ana" titulo="Analíticas" activo={analiticas} onChange={setAnaliticas} texto="Miden el uso del sitio con datos anonimizados." />
            <Interruptor id="ck-mkt" titulo="Marketing" activo={marketing} onChange={setMarketing} texto="Publicidad y medición de campañas." />
          </div>
        )}
        <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" className={btn} onClick={() => decidir(false, false)}>Rechazar las no necesarias</button>
          {configurando
            ? <button type="button" className={btn} onClick={() => decidir(analiticas, marketing)}>Guardar mi elección</button>
            : <button type="button" className={btn} onClick={() => setConfigurando(true)}>Configurar</button>}
          <button type="button" className={btn} onClick={() => decidir(true, true)}>Aceptar todas</button>
        </div>
      </div>
    </div>
  )
}
