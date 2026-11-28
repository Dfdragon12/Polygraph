import { useEffect, useRef } from 'react'

// PC-06 · Lo que TODA ventana (Modal, ConfirmModal y los paneles laterales Publicar, Emitir, Enviar enlace y Solicitar corrección) cumple, en un solo lugar:
//   · el foco entra a la ventana al abrirla (al elemento con data-autofocus, o al primer control, o a la ventana misma);
//   · Esc la cierra (solo la de encima si hay varias);
//   · al cerrarla —por Esc, ✕, Cancelar o porque la acción terminó— el foco vuelve al botón que la abrió;
//   · el resto de la página queda `inert` (no recibe foco, clics ni lector de pantalla) y no se desplaza detrás.
// La ventana debe pintarse con createPortal en document.body: lo que está dentro de #root queda inerte mientras haya una ventana abierta.

const FOCALIZABLES = 'button:not([disabled]), a[href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

const pila = []   // ventanas abiertas, la última es la activa
let ultimoDisparador = null   // Safari no da foco a un botón al hacer clic: se recuerda dónde se hizo el último clic

if (typeof document !== 'undefined') {
  document.addEventListener('pointerdown', (e) => {
    ultimoDisparador = e.target instanceof Element ? e.target.closest('button, a[href], [role="button"], summary') : null
  }, true)
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape' || e.defaultPrevented || pila.length === 0) return
    const tope = pila[pila.length - 1]
    if (tope.alEscape.current) tope.alEscape.current(e)
  })
}

function aplicar() {
  const hay = pila.length > 0
  document.body.style.overflow = hay ? 'hidden' : ''
  const raiz = document.getElementById('root')
  if (raiz) { if (hay) raiz.setAttribute('inert', ''); else raiz.removeAttribute('inert') }
  pila.forEach((v, i) => {
    if (!v.capa) return
    if (i === pila.length - 1) v.capa.removeAttribute('inert'); else v.capa.setAttribute('inert', '')
  })
}

/**
 * @param capa      ref del elemento raíz de la ventana (el fondo oscuro que contiene el diálogo)
 * @param alEscape  qué hacer con Esc (cerrar). Sin función, Esc no hace nada (p. ej. un aviso que no se puede descartar).
 */
export function useVentana({ capa, alEscape }) {
  const alEscapeRef = useRef(alEscape)
  alEscapeRef.current = alEscape
  // Quién la abrió se anota en el PRIMER render, antes de que la ventana (o un campo con autoFocus) se lleve el foco.
  const quienAbrio = useRef(undefined)
  if (quienAbrio.current === undefined && typeof document !== 'undefined') {
    const activo = document.activeElement
    quienAbrio.current = activo && activo !== document.body ? activo : ultimoDisparador
  }

  useEffect(() => {
    const abridor = quienAbrio.current
    const entrada = { capa: capa.current, alEscape: alEscapeRef }
    pila.push(entrada)
    aplicar()

    const el = capa.current
    if (el && !el.contains(document.activeElement)) {
      const destino = el.querySelector('[data-autofocus]') ?? el.querySelector(FOCALIZABLES)
      if (destino) destino.focus({ preventScroll: true })
      else { el.setAttribute('tabindex', '-1'); el.focus({ preventScroll: true }) }
    }

    return () => {
      const i = pila.indexOf(entrada)
      if (i >= 0) pila.splice(i, 1)
      aplicar()
      if (abridor && abridor.isConnected && typeof abridor.focus === 'function') abridor.focus({ preventScroll: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
}
