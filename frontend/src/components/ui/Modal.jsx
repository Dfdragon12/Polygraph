import { createPortal } from 'react-dom'
import { useId, useRef } from 'react'
import { useVentana } from '../../hooks/useVentana'

// PC-06: las dos ventanas genéricas cumplen lo mismo que la ventana Publicar (hooks/useVentana): role="dialog", aria-modal, título asociado
// (aria-labelledby), el foco entra al abrir, Esc cierra, el foco vuelve al botón que la abrió, el fondo queda inerte y sin desplazarse.

/**
 * Modal base — encabezado sólido color primario, usado en toda la plataforma
 * para formularios y vistas de detalle dentro de un modal.
 *
 * Se renderiza con un portal a document.body: si no, cuando se abre desde un
 * componente que vive dentro de una tabla (ej. una fila expandible), su <div>
 * termina como hijo directo de <tbody>, que es HTML inválido — React lo avisa
 * en consola aunque visualmente no se note (por el position: fixed).
 */
export function Modal({ titulo, subtitulo, onClose, ancho = 'max-w-md', children }) {
  const idTitulo = useId()
  const capa = useRef(null)
  useVentana({ capa, alEscape: onClose })
  return createPortal(
    <div ref={capa} className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 overscroll-contain">
      <div role="dialog" aria-modal="true" aria-labelledby={idTitulo} className={`bg-white rounded-2xl shadow-xl w-full ${ancho} max-h-[90vh] flex flex-col`}>
        <div className="px-6 py-4 bg-primary-600 rounded-t-2xl flex items-center justify-between flex-shrink-0">
          <div className="min-w-0">
            <h3 id={idTitulo} className="text-base font-semibold text-white truncate">{titulo}</h3>
            {subtitulo && <p className="text-xs text-primary-100 mt-0.5 truncate">{subtitulo}</p>}
          </div>
          {onClose && (
            <button type="button" onClick={onClose} aria-label="Cerrar"
              className="text-primary-100 hover:text-white text-xl leading-none flex-shrink-0 ml-3 min-h-[44px] min-w-[44px] -my-2 -mr-2 rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-white">
              <span aria-hidden="true">×</span>
            </button>
          )}
        </div>
        <div className="overflow-y-auto overscroll-contain flex-1 scrollbar-thin">{children}</div>
      </div>
    </div>,
    document.body
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
  onClose, // opcional: si se pasa, muestra una X arriba a la derecha (independiente de los botones)
  sinBotones = false,
  bloquearFondo = false, // evita cerrar con un click accidental fuera del modal, para acciones donde eso sería indeseado (ej. cerrar sesión)
  enfocarConfirmar = false, // el foco entra al botón de confirmar (y no al primero de la ventana): para «Seguir conectado», que no debe quedar tras «Cerrar sesión»
  rol = 'dialog', // 'alertdialog' para un aviso que pide una respuesta (p. ej. «¿Sigues ahí?»): el lector lo anuncia con su descripción
  children,
}) {
  const idTitulo = useId()
  const idTexto = useId()
  const capa = useRef(null)
  useVentana({ capa, alEscape: onClose ?? (bloquearFondo ? undefined : onCancelar) })
  const accent = danger
    ? { circulo: 'bg-red-100', icono: 'text-red-500', boton: 'bg-red-600 hover:bg-red-700' }
    : { circulo: 'bg-primary-100', icono: 'text-primary-600', boton: 'bg-primary-600 hover:bg-primary-700' }

  return createPortal(
    <div ref={capa} className="fixed inset-0 z-50 flex items-center justify-center p-4 overscroll-contain">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={bloquearFondo ? undefined : onCancelar} />
      <div role={rol} aria-modal="true" aria-labelledby={idTitulo} aria-describedby={rol === 'alertdialog' ? idTexto : undefined}
        className="relative bg-white rounded-2xl shadow-xl border border-gray-200 w-full max-w-md">
        {onClose && (
          <button type="button" onClick={onClose} aria-label="Cerrar"
            className="absolute top-2 right-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg min-h-[44px] min-w-[44px] flex items-center justify-center transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600">
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
        <div className="px-6 py-5">
          <div className="flex items-start gap-4">
            <div className={`w-10 h-10 rounded-full ${accent.circulo} flex items-center justify-center flex-shrink-0 mt-0.5`}>
              <span className={accent.icono}>{icono}</span>
            </div>
            <div className="flex-1 min-w-0 text-left">
              <h3 id={idTitulo} className="text-base font-semibold text-gray-900 mb-1">{titulo}</h3>
              <div id={idTexto}>{children}</div>
            </div>
          </div>
        </div>
        {!sinBotones && (
          <div className="px-6 pb-5 flex justify-end gap-3">
            <button type="button" onClick={onCancelar}
              className="px-4 py-2 min-h-[44px] text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600">
              {cancelLabel}
            </button>
            <button type="button" onClick={onConfirmar} disabled={cargando} data-autofocus={enfocarConfirmar ? '' : undefined}
              className={`px-4 py-2 min-h-[44px] text-sm font-medium text-white disabled:opacity-50 rounded-lg transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600 ${accent.boton}`}>
              {cargando ? 'Procesando...' : confirmLabel}
            </button>
          </div>
        )}
      </div>
    </div>,
    document.body
  )
}
