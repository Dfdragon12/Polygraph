/**
 * En celular las tablas con la clase "table-stack" se muestran como tarjetas (ver index.css) y cada
 * celda lleva delante el nombre de su columna (atributo data-label). Para no escribirlo a mano en
 * cada <td>, esto lo copia del <th> de la misma columna en todas las tablas "table-stack" de la
 * página, también en las que se dibujan después (cambio de página, búsqueda, recarga).
 * Una celda que ya trae su data-label se respeta; una columna sin título (acciones) queda sin etiqueta.
 */

// Quita los íconos de orden (↕ ↑ ↓ ▲ ▼) y los puntos de la columna de acciones
const limpiar = (texto) => texto.replace(/[↕↑↓▲▼·…]/g, '').replace(/\s+/g, ' ').trim()

function etiquetar(tabla) {
  const encabezados = tabla.querySelectorAll('thead tr:last-child th')
  if (!encabezados.length) return
  // Una entrada por columna real (un <th> con colSpan ocupa varias)
  const titulos = []
  for (const th of encabezados) {
    const texto = limpiar(th.textContent ?? '')
    for (let i = 0; i < (th.colSpan || 1); i++) titulos.push(texto)
  }
  for (const fila of tabla.tBodies.length ? [...tabla.tBodies].flatMap(b => [...b.rows]) : []) {
    let columna = 0
    for (const celda of fila.cells) {
      const titulo = titulos[columna]
      // Las filas de "sin resultados" ocupan todo el ancho: no llevan etiqueta
      if (celda.colSpan === 1 && titulo && !celda.hasAttribute('data-label')) celda.setAttribute('data-label', titulo)
      columna += celda.colSpan || 1
    }
  }
}

let pendiente = false
function etiquetarTodas() {
  pendiente = false
  document.querySelectorAll('table.table-stack').forEach(etiquetar)
}

/** Se llama una vez al arrancar la aplicación. */
export function activarEtiquetasTablas() {
  if (typeof MutationObserver === 'undefined') return
  new MutationObserver(() => {
    if (pendiente) return
    pendiente = true
    requestAnimationFrame(etiquetarTodas)
  }).observe(document.body, { childList: true, subtree: true })
}
