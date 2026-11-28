import { CRITERIOS, CRITERIO_POR_DEFECTO, estudioSegunCriterio, instantanea, leerInstantanea, cambioDeclaracion } from './reglasAcademica'
import { useState } from 'react'
import { NoImprime } from './AreaTexto'
import { precarga, hayVerificado, CLAVES_PRECARGA, CLAVES_VERIFICACION, SIN_CODIGO } from './reglasEstudio'

// AC-02 · Fase 1 de la verificación académica: criterio de verificación y selección del estudio. Se guardan las columnas de la plantilla (aca_criterio, que se imprime;
// aca_estudio_sel y aca_decl, que no): el estudio elegido y una copia de lo que declaró el evaluado en ese momento.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const delExterior = (e) => /^s[ií]$/i.test(String(e.en_exterior ?? ''))

/** Acceso por clave a la sección «5. Estudio verificado» (donde viven el criterio y la selección). */
export function accesoEstudio(secciones, editarCampo) {
  const s = secciones.find(x => x.clave === 'estudio_verificado' && !x.origenFijoClave)
  const col = (clave) => s?.columnas.find(c => c.clave === clave)
  return {
    existe: !!s,
    val: (clave) => { const c = col(clave); return c ? (s.valores[c.idColumna] ?? '') : '' },
    poner: (clave, v) => { const c = col(clave); if (c && s) editarCampo(s.idSeccion, c.idColumna, v) },
    opciones: (clave) => (col(clave)?.opciones ?? []).filter(Boolean),
    valores: () => Object.fromEntries((s?.columnas ?? []).map(c => [c.clave, s.valores[c.idColumna] ?? ''])),
  }
}

/** Las listas que se usan para llevar lo declarado a una opción (nivel y estado). */
export const listasDeEstudio = (a) => ({ aca_nivel: a.opciones('aca_nivel'), aca_estado: a.opciones('aca_estado') })

/**
 * Selecciona un estudio: guarda su id y la copia de lo que declaró el evaluado. Con `conPrecarga` (lo normal) también llena las celdas de «5. Estudio verificado» con lo declarado;
 * con `limpiar` borra además lo que el analista había verificado (documento, medio, contacto, observaciones, preguntas y calificación).
 */
export function ponerSeleccion(acceso, estudio, { conPrecarga = true, limpiar = false } = {}) {
  acceso.poner('aca_estudio_sel', String(estudio.id))
  acceso.poner('aca_decl', instantanea(estudio))
  if (!conPrecarga) return
  const p = precarga(estudio, listasDeEstudio(acceso))
  CLAVES_PRECARGA.forEach(k => acceso.poner(k, p[k] ?? ''))
  if (limpiar) { CLAVES_VERIFICACION.forEach(k => acceso.poner(k, '')); acceso.poner('aca_ref', SIN_CODIGO) }
}

function TarjetaEstudio({ estudio, seleccionada, elegible, onElegir }) {
  const lineas = [
    [estudio.nivel, estudio.institucion].filter(tiene).join(' · '),
    [estudio.ciudad, delExterior(estudio) ? `En el exterior${tiene(estudio.pais_exterior) ? ` (${estudio.pais_exterior})` : ''}` : null].filter(tiene).join(' · '),
    `${estudio.fecha_inicio || '—'} → ${estudio.fecha_fin || '—'}`,
  ].filter(tiene)
  return (
    <button type="button" role="radio" aria-checked={seleccionada} disabled={!elegible && !seleccionada} onClick={() => elegible && onElegir(estudio)}
      className={`text-left w-full rounded-lg border p-3 ${seleccionada ? 'border-primary-600 bg-primary-50 ring-1 ring-primary-600' : 'border-gray-300 bg-white'} ${elegible ? 'hover:bg-gray-50' : 'cursor-default'}`}>
      <span className="flex items-start justify-between gap-2">
        <span className="font-semibold text-gray-900 break-words">{estudio.titulo || estudio.nivel || 'Estudio sin título'}</span>
        {seleccionada && <span className="text-xs font-semibold text-primary-700 whitespace-nowrap">✓ Seleccionado</span>}
      </span>
      {lineas.map((l, i) => <span key={i} className="block text-xs text-gray-700 break-words">{l}</span>)}
      {tiene(estudio.estado) && <span className="block text-xs font-medium text-gray-800 mt-1">{estudio.estado}</span>}
    </button>
  )
}

export function EducacionCriterio({ secciones, estudios, sinEstudios, soloLectura, onCampo }) {
  const a = accesoEstudio(secciones, onCampo)
  const [pendiente, setPendiente] = useState(null)   // estudio que el analista tocó mientras había datos verificados: espera su respuesta
  if (!a.existe) return <p className="text-gray-700">Este formato no tiene la sección del estudio verificado.</p>
  const criterio = a.val('aca_criterio') || CRITERIO_POR_DEFECTO
  const seleccionId = a.val('aca_estudio_sel')
  const copia = leerInstantanea(a.val('aca_decl'))
  const segun = estudioSegunCriterio(criterio, estudios, sinEstudios)
  const cambio = cambioDeclaracion(seleccionId, copia, estudios)
  const elegible = !soloLectura && !segun.automatico && estudios.length > 0

  const elegir = (x) => {
    if (String(x.id) === String(seleccionId)) return
    if (tiene(seleccionId) && hayVerificado(a.valores(), copia, listasDeEstudio(a))) setPendiente(x)
    else ponerSeleccion(a, x)
  }
  const resolver = (como) => {
    if (como === 'reemplazar') ponerSeleccion(a, pendiente, { limpiar: true })
    else if (como === 'conservar') ponerSeleccion(a, pendiente, { conPrecarga: false })
    setPendiente(null)
  }

  return (
    <div className="space-y-5">
      <section aria-labelledby="ac-criterio" data-clave="aca_criterio">
        <h3 id="ac-criterio" className="text-sm font-bold text-gray-900 mb-2">Criterio de verificación</h3>
        <div role="radiogroup" aria-label="Criterio de verificación" className="flex flex-wrap gap-2">
          {CRITERIOS.map(o => (
            <button key={o} type="button" role="radio" aria-checked={criterio === o} disabled={soloLectura} onClick={() => a.poner('aca_criterio', o)}
              className={`min-h-11 px-3 text-sm font-medium rounded-lg border ${criterio === o ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'} disabled:opacity-60`}>{o}</button>
          ))}
        </div>
        {criterio !== CRITERIO_POR_DEFECTO && (
          <p className="mt-2 text-xs text-amber-900">Con este criterio, <b>Observaciones</b> (en «Estudio verificado») debe explicar por qué se verifica este estudio.</p>
        )}
      </section>

      <section aria-labelledby="ac-sel" data-clave="aca_estudio_sel">
        <div className="flex items-center justify-between gap-2 mb-2">
          <h3 id="ac-sel" className="text-sm font-bold text-gray-900">Estudio a verificar</h3>
          <NoImprime />
        </div>
        {segun.mensaje && <p className={`text-sm mb-2 ${estudios.length === 0 ? 'text-gray-800' : 'text-amber-900'}`} role={estudios.length === 0 ? undefined : 'status'}>{segun.mensaje}</p>}
        {segun.automatico && <p className="text-xs text-gray-700 mb-2">Se selecciona solo el último estudio finalizado. Para verificar otro, cambia el criterio.</p>}
        {estudios.length > 0 && (
          <div role="radiogroup" aria-label="Estudio a verificar" className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {estudios.map(e => (
              <TarjetaEstudio key={e.id} estudio={e} seleccionada={String(e.id) === String(seleccionId)} elegible={elegible} onElegir={elegir} />
            ))}
          </div>
        )}
        {pendiente && (
          <div role="alertdialog" aria-label="Reemplazar lo verificado" className="mt-2 text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-3">
            <p className="font-semibold">¿Reemplazar lo verificado con el nuevo estudio?</p>
            <p className="mb-2">Ya hay datos confirmados del estudio anterior. «Reemplazar» los cambia por lo que declaró el evaluado del nuevo estudio y borra lo verificado (documento, medio, contacto, observaciones y preguntas). «Conservar» solo cambia el estudio seleccionado.</p>
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={() => resolver('reemplazar')} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-primary-600 bg-primary-600 text-white">Reemplazar</button>
              <button type="button" onClick={() => resolver('conservar')} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-800">Conservar lo confirmado</button>
              <button type="button" onClick={() => resolver('cancelar')} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-800">Cancelar</button>
            </div>
          </div>
        )}
        {cambio === 'cambio' && <p role="alert" className="mt-2 text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">El evaluado cambió su declaración de este estudio después de que lo seleccionaras. Revisa los datos confirmados.</p>}
        {cambio === 'desaparecio' && <p role="alert" className="mt-2 text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">Este estudio ya no aparece en la hoja de vida del evaluado. Revisa cuál se debe verificar.</p>}
      </section>
    </div>
  )
}
