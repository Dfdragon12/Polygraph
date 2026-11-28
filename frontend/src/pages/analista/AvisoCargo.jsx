import { useState } from 'react'

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// LB-07 · El cargo se muestra tal cual está en la solicitud (solo lectura). Si tiene un error, el analista avisa al gestor con una novedad; el dato no se corrige desde aquí.
export default function AvisoCargo({ cargo, onAvisar, clave }) {
  const [abierto, setAbierto] = useState(false)
  const [correcto, setCorrecto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [avisado, setAvisado] = useState(false)
  const enviar = async () => {
    setEnviando(true)
    try {
      await onAvisar(clave, `El cargo de la solicitud dice «${cargo || 'sin dato'}» y tiene un error.${tiene(correcto) ? ` Cargo correcto: «${correcto.trim()}».` : ''} El cargo de la solicitud no se modificó.`)
      setAvisado(true); setAbierto(false); setCorrecto('')
    } catch { /* el aviso de error ya lo muestra la pantalla */ } finally { setEnviando(false) }
  }
  if (avisado) return <p className="text-xs text-green-800">Aviso enviado al gestor: el cargo tiene un error.</p>
  return (
    <div className="text-xs">
      <button type="button" onClick={() => setAbierto(a => !a)} aria-expanded={abierto} className="min-h-11 underline text-primary-700">Avisar al gestor: el cargo tiene un error</button>
      {abierto && (
        <div className="mt-1 space-y-2 max-w-sm bg-white border border-gray-200 rounded p-2">
          <p className="text-gray-800">Cargo en la solicitud: <b className="break-words">{cargo || 'sin dato'}</b></p>
          <label htmlFor="lb-cargo-ok" className="block font-semibold text-gray-700">Cargo correcto (opcional)</label>
          <input id="lb-cargo-ok" className="w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm" maxLength={150} value={correcto} onChange={e => setCorrecto(e.target.value)} />
          <button type="button" disabled={enviando} onClick={enviar} className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 text-white disabled:opacity-50">{enviando ? 'Avisando…' : 'Avisar al gestor'}</button>
        </div>
      )}
    </div>
  )
}
