import { useState, useEffect, useCallback } from 'react'
import soporteService from '../services/soporteService'
import Toast from './Toast'
import { Modal } from './ui/Modal'

const ESTADO_CFG = {
  null:       { label: 'Sin cargar',  badge: 'bg-gray-100 text-gray-500' },
  PENDIENTE:  { label: 'En revisión', badge: 'bg-amber-100 text-amber-700' },
  VALIDADO:   { label: 'Vigente',     badge: 'bg-green-100 text-green-700' },
  RECHAZADO:  { label: 'Rechazado',   badge: 'bg-red-100 text-red-600' },
  VENCIDO:    { label: 'Vencido',     badge: 'bg-orange-100 text-orange-700' },
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

/* ─── Sub-modal: validar / rechazar ─── */
function ModalValidar({ soporte, accion, onConfirmar, onCancelar }) {
  const [observaciones, setObservaciones] = useState('')
  const [enviando, setEnviando] = useState(false)
  const esRechazo = accion === 'rechazar'

  const confirmar = async () => {
    setEnviando(true)
    await onConfirmar(observaciones)
    setEnviando(false)
  }

  return (
    <Modal
      titulo={esRechazo ? 'Rechazar documento' : 'Validar documento'}
      subtitulo={soporte.nombreTipo}
      onClose={onCancelar} ancho="max-w-md">
      <div className="px-6 py-5 space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Observaciones {esRechazo ? '(obligatorio)' : '(opcional)'}
          </label>
          <textarea value={observaciones} onChange={e => setObservaciones(e.target.value)} rows={3}
            placeholder={esRechazo ? 'Explica por qué se rechaza…' : 'Notas adicionales…'}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>
        <div className="flex justify-end gap-3 pt-1">
          <button onClick={onCancelar} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button onClick={confirmar} disabled={enviando || (esRechazo && !observaciones.trim())}
            className={`px-5 py-2 text-sm font-medium text-white rounded-lg transition-colors disabled:opacity-60 ${
              esRechazo ? 'bg-red-600 hover:bg-red-700' : 'bg-primary-600 hover:bg-primary-700'
            }`}>
            {enviando ? 'Procesando...' : (esRechazo ? 'Rechazar' : 'Validar')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/* ─── Tarjeta por tipo de documento ─── */
function TarjetaSoporte({ soporte, onCambio, onToast }) {
  const [descargando, setDescargando] = useState(null)
  const [revisando, setRevisando] = useState(null) // 'validar' | 'rechazar'
  const cfg = ESTADO_CFG[soporte.estado] ?? ESTADO_CFG.null

  const descargar = async () => {
    setDescargando(true)
    try {
      const blob = await soporteService.descargarAdmin(soporte.idSoporte)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = soporte.nombreArchivo || 'documento'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      onToast('No se pudo descargar el documento', 'error')
    } finally {
      setDescargando(false)
    }
  }

  const resolver = async (observaciones) => {
    try {
      await soporteService.validarAdmin(soporte.idSoporte, revisando === 'validar', observaciones || null)
      onToast(revisando === 'validar' ? 'Documento validado.' : 'Documento rechazado.')
      setRevisando(null)
      onCambio()
    } catch (e) {
      onToast(e.response?.data?.mensaje ?? 'Error al procesar el documento.', 'error')
    }
  }

  return (
    <div className="border border-gray-100 rounded-xl p-4 space-y-2.5">
      <div className="flex items-start justify-between gap-3">
        <h4 className="text-sm font-semibold text-gray-800 leading-snug">{soporte.nombreTipo}</h4>
        <span className={`flex-shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${cfg.badge}`}>{cfg.label}</span>
      </div>

      <div className="text-xs text-gray-500 space-y-0.5">
        {soporte.nombreArchivo && <p>Cargado: {formatFecha(soporte.fechaEntrega)}</p>}
        {soporte.fechaVencimiento && <p>Vence: {formatFecha(soporte.fechaVencimiento)}</p>}
        {!soporte.nombreArchivo && <p className="text-gray-400">El cliente aún no ha cargado este documento.</p>}
      </div>

      {soporte.estado === 'RECHAZADO' && soporte.observaciones && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg">
          <strong>Motivo del rechazo:</strong> {soporte.observaciones}
        </div>
      )}

      {soporte.nombreArchivo && (
        <div className="flex items-center gap-1.5 pt-1">
          <button onClick={descargar} disabled={descargando}
            className="text-xs font-medium px-2.5 py-1.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50 transition-colors">
            {descargando ? '...' : 'Descargar'}
          </button>
          {soporte.estado === 'PENDIENTE' && (
            <>
              <button onClick={() => setRevisando('validar')}
                className="text-xs font-medium px-2.5 py-1.5 rounded-lg bg-primary-600 hover:bg-primary-700 text-white transition-colors">
                Validar
              </button>
              <button onClick={() => setRevisando('rechazar')}
                className="text-xs font-medium px-2.5 py-1.5 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition-colors">
                Rechazar
              </button>
            </>
          )}
        </div>
      )}

      {revisando && (
        <ModalValidar soporte={soporte} accion={revisando} onConfirmar={resolver} onCancelar={() => setRevisando(null)} />
      )}
    </div>
  )
}

/* ─── Modal principal: documentos del cliente ─── */
export default function ModalDocumentosCliente({ cliente, onClose }) {
  const [soportes, setSoportes] = useState(null)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const cargar = useCallback(() => {
    soporteService.listarAdmin(cliente.idCliente)
      .then(setSoportes)
      .catch(() => setError('No se pudieron cargar los documentos de este cliente.'))
  }, [cliente.idCliente])

  useEffect(() => { cargar() }, [cargar])

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  return (
    <Modal titulo="Documentos legales" subtitulo={cliente.nombreDisplay} onClose={onClose} ancho="max-w-2xl">
      <div className="px-6 py-5">
        {error ? (
          <p className="text-sm text-red-500 text-center py-8">{error}</p>
        ) : soportes === null ? (
          <div className="flex justify-center py-10">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" />
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {soportes.map(s => (
              <TarjetaSoporte key={s.tipoSoporte} soporte={s} onCambio={cargar} onToast={mostrarToast} />
            ))}
          </div>
        )}
      </div>
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </Modal>
  )
}
