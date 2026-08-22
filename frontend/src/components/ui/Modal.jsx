/**
 * Modal base — encabezado sólido color primario, usado en toda la plataforma
 * para formularios y vistas de detalle dentro de un modal.
 */
export function Modal({ titulo, subtitulo, onClose, ancho = 'max-w-md', children }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className={`bg-white rounded-2xl shadow-xl w-full ${ancho} max-h-[90vh] flex flex-col`}>
        <div className="px-6 py-4 bg-primary-600 rounded-t-2xl flex items-center justify-between flex-shrink-0">
          <div className="min-w-0">
            <h3 className="text-base font-semibold text-white truncate">{titulo}</h3>
            {subtitulo && <p className="text-xs text-primary-100 mt-0.5 truncate">{subtitulo}</p>}
          </div>
          {onClose && (
            <button onClick={onClose} className="text-primary-100 hover:text-white text-xl leading-none flex-shrink-0 ml-3">×</button>
          )}
        </div>
        <div className="overflow-y-auto flex-1">{children}</div>
      </div>
    </div>
  )
}

const ICONO_ALERTA = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
  </svg>
)

/**
 * Modal de confirmación — icono + título + cuerpo libre + acciones.
 * Usa color primario por defecto; pasar `danger` solo para acciones
 * destructivas (eliminar) donde se conserva el rojo por seguridad de UX.
 */
export function ConfirmModal({
  titulo,
  icono = ICONO_ALERTA,
  danger = false,
  cargando = false,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  onConfirmar,
  onCancelar,
  children,
}) {
  const accent = danger
    ? { circulo: 'bg-red-100', icono: 'text-red-500', boton: 'bg-red-600 hover:bg-red-700' }
    : { circulo: 'bg-primary-100', icono: 'text-primary-600', boton: 'bg-primary-600 hover:bg-primary-700' }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCancelar} />
      <div className="relative bg-white rounded-2xl shadow-xl border border-gray-200 w-full max-w-md">
        <div className="px-6 py-5">
          <div className="flex items-start gap-4">
            <div className={`w-10 h-10 rounded-full ${accent.circulo} flex items-center justify-center flex-shrink-0 mt-0.5`}>
              <span className={accent.icono}>{icono}</span>
            </div>
            <div className="flex-1 min-w-0 text-left">
              <h3 className="text-base font-semibold text-gray-900 mb-1">{titulo}</h3>
              {children}
            </div>
          </div>
        </div>
        <div className="px-6 pb-5 flex justify-end gap-3">
          <button onClick={onCancelar}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
            {cancelLabel}
          </button>
          <button onClick={onConfirmar} disabled={cargando}
            className={`px-4 py-2 text-sm font-medium text-white disabled:opacity-50 rounded-lg transition-colors ${accent.boton}`}>
            {cargando ? 'Procesando...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
