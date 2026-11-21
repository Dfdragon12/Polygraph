import { useEffect, useState } from 'react'
import api from '../../services/api'
import { Modal } from '../../components/ui/Modal'
import { formatearPrecio } from '../../utils/catalogoDisplay'

/** TP-13 · Los clientes pospago actuales y lo que les falta de la lista de verificación. Solo informa: nunca quita un cupo. */
export default function ReportePospago({ onClose, onVer }) {
  const [filas, setFilas] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => {
    api.get('/clientes/pospago/reporte').then((r) => setFilas(r.data)).catch(() => setError('No se pudo cargar el reporte.'))
  }, [])

  return (
    <Modal titulo="Clientes pospago y requisitos pendientes" ancho="max-w-4xl" onClose={onClose}>
      <div className="px-6 py-5 space-y-3">
        <p className="text-xs text-gray-600">Los clientes pospago actuales siguen operando con su cupo. Tú decides si completas lo que falta o los pasas a prepago.</p>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        {!filas && !error && <p className="text-sm text-gray-500">Cargando…</p>}
        {filas && filas.length === 0 && <p className="text-sm text-gray-500">No hay clientes pospago.</p>}
        {filas && filas.length > 0 && (
          <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200">
            {filas.map((f) => (
              <li key={f.idCliente} className="px-4 py-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-semibold text-gray-800">{f.nombre} <span className="font-normal text-gray-500">· NIT {f.nit ?? '—'} · cupo {f.cupo != null ? formatearPrecio(f.cupo) : '—'}</span></p>
                  <button type="button" onClick={() => onVer(f.idCliente)} className="text-xs font-semibold text-primary-700 underline">Ver requisitos</button>
                </div>
                {f.cumpleTodo ? <p className="text-xs text-emerald-700 mt-1">Cumple toda la lista.</p> : (
                  <ul className="mt-1 list-disc pl-5 text-xs text-red-700">{f.faltantes.map((x, i) => <li key={i}>{x}</li>)}</ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
