import { useState } from 'react'

// VD-02 · «Datos generales» de la visita. Los datos que se guardan son los mismos de la plantilla (claves v_*, dc_*, ev_*): solo cambia cómo se
// capturan. El Word sigue leyendo las mismas columnas.

const NO_APLICA = '—'
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// Filas de «Información registrada por el candidato»: [etiqueta, prefijo de las claves dc_*]
export const FILAS_REGISTRO = [
  ['Ciudad de residencia', 'dc_ciudad'], ['Barrio de residencia', 'dc_barrio'], ['Dirección de residencia', 'dc_direccion'],
  ['Adicionales (casa, torre, apartamento)', 'dc_adicionales'], ['Estrato', 'dc_estrato'], ['Estado civil', 'dc_estado_civil'],
]

export const resultadoDeFila = (registrado, confirmado) => {
  if (!tiene(confirmado)) return ''
  return String(registrado ?? '').trim().toLowerCase() === String(confirmado).trim().toLowerCase() ? 'Coincide' : 'Corregido'
}

// Botones de una sola elección (una lista corta se ve y se toca mejor que un desplegable).
export function Botones({ etiqueta, opciones, valor, onElegir, deshabilitado, id }) {
  return (
    <div role="radiogroup" aria-labelledby={id} className="flex flex-wrap gap-2">
      {opciones.map(o => {
        const activo = valor === o
        return (
          <button key={o} type="button" role="radio" aria-checked={activo} disabled={deshabilitado} onClick={() => onElegir(o)}
            aria-label={`${etiqueta}: ${o}`}
            className={`min-h-11 px-4 text-sm font-medium rounded-lg border ${activo ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'} disabled:opacity-60`}>
            {o}
          </button>
        )
      })}
    </div>
  )
}

const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'

function Etiqueta({ id, children }) {
  return <p id={id} className="text-xs font-semibold text-gray-700 mb-1">{children}</p>
}

export default function DatosGeneralesVisita({
  seccion, soloLectura, criticidad, modulos, onCampo, onModulo, onCambioConfig, onAvisarCargo, cargoSolicitud, cita,
}) {
  const col = (clave) => seccion.columnas.find(c => c.clave === clave)
  const val = (clave) => { const c = col(clave); return c ? (seccion.valores[c.idColumna] ?? '') : '' }
  const poner = (clave, valor) => { const c = col(clave); if (c) onCampo(seccion.idSeccion, c.idColumna, valor) }
  const [cargoReal, setCargoReal] = useState('')
  const [avisado, setAvisado] = useState(false)
  const [avisando, setAvisando] = useState(false)
  const sl = soloLectura

  const elegirConfig = (clave, etiqueta, nuevo) => {
    const antes = val(clave)
    if (antes === nuevo) return
    poner(clave, nuevo)
    onCambioConfig(clave, etiqueta, antes, nuevo)
  }

  // Al corregir «confirmado» el resultado lo pone el sistema.
  const editarConfirmado = (prefijo, valor) => {
    poner(`${prefijo}_vis`, valor)
    poner(`${prefijo}_res`, resultadoDeFila(val(`${prefijo}_reg`), valor))
  }

  const avisarCargo = async () => {
    setAvisando(true)
    try { await onAvisarCargo(cargoReal.trim()); setAvisado(true) } finally { setAvisando(false) }
  }

  const solicitud = [
    ['N° de servicio', val('estudio_numero')], ['Cliente', val('ev_cliente')], ['Cargo', val('ev_cargo') || cargoSolicitud],
    ['Profesional a cargo', val('v_profesional')],
  ]
  const residencia = val('v_residencia')

  return (
    <div className="space-y-6">
      {/* Configuración: no se imprime */}
      <section aria-labelledby="vd-config" className="rounded-lg border border-purple-200 bg-purple-50/50 p-4 space-y-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 id="vd-config" className="text-sm font-bold text-purple-900">Configuración de la visita</h3>
          <span className="text-xs text-purple-800">No se imprime</span>
        </div>
        <div>
          <Etiqueta id="vd-tipo">Tipo de visita</Etiqueta>
          <Botones id="vd-tipo" etiqueta="Tipo de visita" opciones={['Presencial', 'Virtual']} valor={val('v_tipo')} deshabilitado={sl}
            onElegir={v => elegirConfig('v_tipo', 'Tipo de visita', v)} />
        </div>
        <div>
          <Etiqueta id="vd-clase">Clase de visita</Etiqueta>
          <Botones id="vd-clase" etiqueta="Clase de visita" opciones={['Pre-empleo', 'Rutina']} valor={val('v_clase')} deshabilitado={sl}
            onElegir={v => elegirConfig('v_clase', 'Clase de visita', v)} />
        </div>
        <div>
          <Etiqueta id="vd-modulos">Módulos adicionales</Etiqueta>
          <div role="group" aria-labelledby="vd-modulos" className="flex flex-wrap gap-2">
            {modulos.map(m => (
              <button key={m.nombre} type="button" aria-pressed={m.activo} disabled={sl} onClick={() => onModulo(m.nombre, !m.activo)}
                className={`min-h-11 px-4 text-sm font-medium rounded-lg border ${m.activo ? 'border-purple-700 bg-purple-700 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'} disabled:opacity-60`}>
                {m.activo ? '✓ ' : ''}{m.nombre}
              </button>
            ))}
          </div>
          <p className="text-xs text-gray-700 mt-1">Al activar un módulo sus campos y fotos aparecen en el tema que le corresponde. Al desactivarlo se ocultan y se conservan mientras no cierres esta pantalla.</p>
        </div>
        <p className="text-xs text-gray-700">
          Criticidad del cargo: <b>{criticidad || 'sin definir'}</b> (viene de la solicitud).
        </p>
        {!sl && (avisado
          ? <p className="text-sm text-green-700" role="status">✓ Aviso enviado al gestor.</p>
          : (
            <details className="text-sm">
              <summary className="cursor-pointer min-h-11 flex items-center text-primary-700 underline">Avisar al gestor: el cargo no coincide</summary>
              <div className="mt-2 space-y-2">
                <label htmlFor="vd-cargo-real" className="block text-xs font-semibold text-gray-700">Cargo real que observaste</label>
                <input id="vd-cargo-real" className={inputCls} value={cargoReal} onChange={e => setCargoReal(e.target.value)} maxLength={200} />
                <button type="button" disabled={avisando || !tiene(cargoReal)} onClick={avisarCargo}
                  className="min-h-11 px-4 text-sm font-medium rounded-lg bg-primary-600 text-white disabled:opacity-50">{avisando ? 'Enviando…' : 'Enviar aviso'}</button>
              </div>
            </details>
          ))}
      </section>

      {/* Datos que vienen de la solicitud */}
      <section aria-labelledby="vd-solicitud">
        <h3 id="vd-solicitud" className="text-sm font-bold text-gray-900 mb-2">Datos de la solicitud</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2">
          {solicitud.map(([e, v]) => (
            <div key={e} className="border-b border-gray-200 pb-1"><dt className="text-xs text-gray-600">{e}</dt><dd className="text-gray-900 break-words">{v || NO_APLICA}</dd></div>
          ))}
          <div className="border-b border-gray-200 pb-1">
            <dt className="text-xs text-gray-600">Fecha de recibo de la orden</dt>
            <dd><input aria-label="Fecha de recibo de la orden" className={inputCls} value={val('v_fecha_orden')} disabled={sl} onChange={e => poner('v_fecha_orden', e.target.value)} placeholder="03/11/2026" /></dd>
          </div>
          <div className="border-b border-gray-200 pb-1">
            <dt className="text-xs text-gray-600">Salario a devengar</dt>
            <dd><input aria-label="Salario a devengar" className={inputCls} value={val('v_salario')} disabled={sl} onChange={e => poner('v_salario', e.target.value)} /></dd>
          </div>
        </dl>
      </section>

      {/* Ciudad y fecha, quién atiende, cómo llegó */}
      <section aria-labelledby="vd-visita" className="space-y-4">
        <h3 id="vd-visita" className="text-sm font-bold text-gray-900">La visita</h3>
        <div>
          <label htmlFor="vd-ciudad-fecha" className="block text-xs font-semibold text-gray-700 mb-1">Ciudad y fecha de visita *</label>
          <input id="vd-ciudad-fecha" className={inputCls} value={val('v_ciudad_fecha')} disabled={sl} onChange={e => poner('v_ciudad_fecha', e.target.value)} placeholder={cita || 'Bogotá, 03/11/2026'} />
        </div>
        <div>
          <label htmlFor="vd-atiende" className="block text-xs font-semibold text-gray-700 mb-1">Quien atiende la visita (nombre y parentesco)</label>
          <input id="vd-atiende" className={inputCls} value={val('v_atiende')} disabled={sl} onChange={e => poner('v_atiende', e.target.value)} />
        </div>
        <div>
          <label htmlFor="vd-hv" className="block text-xs font-semibold text-gray-700 mb-1">Cómo llega su hoja de vida a la empresa</label>
          <input id="vd-hv" className={inputCls} value={val('v_hoja_vida')} disabled={sl} onChange={e => poner('v_hoja_vida', e.target.value)} />
          <div className="mt-2">
            <Etiqueta id="vd-conocido">¿Por un conocido o familiar en la empresa?</Etiqueta>
            <Botones id="vd-conocido" etiqueta="Por un conocido o familiar" opciones={['No', 'Sí']} valor={val('v_hv_conocido')} deshabilitado={sl}
              onElegir={v => poner('v_hv_conocido', v)} />
            {val('v_hv_conocido') === 'Sí' && (
              <p className="text-xs text-amber-800 mt-1" role="note">Escribe arriba, en «Cómo llega su hoja de vida», el nombre y el cargo de esa persona.</p>
            )}
          </div>
        </div>
      </section>

      {/* Información registrada por el candidato */}
      <section aria-labelledby="vd-registro">
        <h3 id="vd-registro" className="text-sm font-bold text-gray-900 mb-2">Información registrada por el candidato</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm border border-gray-300">
            <thead className="bg-gray-100 text-xs text-gray-700">
              <tr>
                <th scope="col" className="text-left p-2 border-b border-gray-300">Dato</th>
                <th scope="col" className="text-left p-2 border-b border-gray-300">Registrado por el candidato</th>
                <th scope="col" className="text-left p-2 border-b border-gray-300">Confirmado en la visita</th>
                <th scope="col" className="text-left p-2 border-b border-gray-300">Resultado</th>
              </tr>
            </thead>
            <tbody>
              {FILAS_REGISTRO.map(([etq, pre]) => {
                const reg = val(`${pre}_reg`)
                return (
                  <tr key={pre} className="border-b border-gray-200 align-top">
                    <th scope="row" className="text-left font-medium p-2 w-1/4">{etq}</th>
                    <td className="p-2 bg-gray-100 text-gray-800 break-words">{tiene(reg) ? reg : <span className="text-gray-600 italic">Sin registro</span>}</td>
                    <td className="p-2"><input aria-label={`${etq}, confirmado en la visita`} className={inputCls} value={val(`${pre}_vis`)} disabled={sl} onChange={e => editarConfirmado(pre, e.target.value)} /></td>
                    <td className="p-2 whitespace-nowrap">{val(`${pre}_res`) || NO_APLICA}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <div className="mt-4">
          <Etiqueta id="vd-reside">¿Reside en la dirección declarada? *</Etiqueta>
          <Botones id="vd-reside" etiqueta="Reside en la dirección declarada" opciones={['Sí', 'No – con explicación', 'No – sin explicación']} valor={residencia}
            deshabilitado={sl} onElegir={v => poner('v_residencia', v)} />
          {residencia === 'No – con explicación' && (
            <div className="mt-2">
              <label htmlFor="vd-reside-det" className="block text-xs font-semibold text-gray-700 mb-1">Explicación * (obligatoria)</label>
              <textarea id="vd-reside-det" rows={2} className={`${inputCls} py-2`} value={val('v_residencia_det')} disabled={sl} onChange={e => poner('v_residencia_det', e.target.value)} />
            </div>
          )}
        </div>
        <div className="mt-4">
          <label htmlFor="vd-dc-obs" className="block text-xs font-semibold text-gray-700 mb-1">Observaciones de los datos</label>
          <textarea id="vd-dc-obs" rows={3} className={`${inputCls} py-2`} value={val('dc_obs')} disabled={sl} onChange={e => poner('dc_obs', e.target.value)} />
        </div>
      </section>
    </div>
  )
}
