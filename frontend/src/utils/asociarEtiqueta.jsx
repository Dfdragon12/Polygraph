import { Children, cloneElement, isValidElement } from 'react'

const CAMPOS = new Set(['input', 'select', 'textarea'])
const SIN_ETIQUETA = new Set(['hidden', 'submit', 'button', 'reset', 'image'])

/**
 * Asocia una etiqueta con su campo (WCAG 1.3.1 / 4.1.2): recorre los hijos, encuentra el PRIMER
 * input/select/textarea que no tenga id y le pone `id`, y — si se piden — `aria-invalid` y
 * `aria-describedby` (ayuda / mensaje de error). Atraviesa envoltorios (ej. un recuadro con ícono)
 * pero no entra en componentes que arman su campo por dentro: esos reciben el id por prop.
 *
 * Se asocia por `htmlFor`/`id` y NO envolviendo el campo en el <label>: un <label> reenvía los
 * clics de su espacio vacío al primer control, y varios campos traen botones junto al input.
 */
export function asociarEtiqueta(hijos, { id, describedby, invalido } = {}) {
  let hecho = false

  const recorrer = (nodo) => {
    if (hecho || !isValidElement(nodo)) return nodo

    if (typeof nodo.type === 'string' && CAMPOS.has(nodo.type)) {
      if (SIN_ETIQUETA.has(nodo.props.type)) return nodo
      hecho = true
      if (nodo.props.id) return nodo // ya tiene id propio: no se pisa
      const extra = { id }
      if (describedby) extra['aria-describedby'] = nodo.props['aria-describedby'] ?? describedby
      if (invalido) extra['aria-invalid'] = true
      return cloneElement(nodo, extra)
    }

    // Componentes que declaran `Componente.aceptaId = true` pasan `id` (y aria-*) a su campo interno
    if (typeof nodo.type === 'function' && nodo.type.aceptaId) {
      hecho = true
      if (nodo.props.id) return nodo
      const extra = { id }
      if (describedby) extra['aria-describedby'] = describedby
      if (invalido) extra['aria-invalid'] = true
      return cloneElement(nodo, extra)
    }

    const interior = nodo.props.children
    if (interior == null || typeof interior === 'string') return nodo
    const nuevos = Children.map(interior, recorrer)
    return hecho ? cloneElement(nodo, undefined, ...nuevos) : nodo
  }

  return Children.map(hijos, recorrer)
}
