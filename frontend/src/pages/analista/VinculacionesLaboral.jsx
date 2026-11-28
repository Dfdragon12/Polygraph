import { useEffect, useMemo, useState } from 'react'
import CampoFecha from '../../components/CampoFecha'
import agendaService from '../../services/agendaService'
import { InputPorTipo } from '../shared/Formulario'
import AreaTexto, { NoImprime } from './AreaTexto'
import GestionesTabla from './GestionesTabla'
import {
  ACTUAL, EMPRESA, JEFE, SABANA, PENDIENTE, PERSONA_NATURAL, LLAMADOS, LLAMADAS_OBLIGATORIAS,
  iguales, soloDigitos, aMoneda, textoAIso, isoATexto, isoAFecha, fechaATexto, fechaAIso, diasEntre,
  compararFecha, compararRetiro, claseDeMotivo, claseDeLlamados, detalleDeLlamados, armarLlamados, dentroDelPeriodo, cambioLaDeclaracion,
  tieneCodigo, conCodigo, preguntasDeCodigo, codigosSugeridos, llamadasQueFaltan, fechaLimite, borradorObservacion,
} from './reglasLaboral'

// LB-03 · Vinculaciones de la verificación laboral. Cada vinculación nace de una experiencia que declaró el evaluado (con lo declarado copiado) y se diligencia como la tabla
// «VINCULACIÓN LABORAL N.º X» del Word: cada celda lleva lo confirmado y, debajo, «Declaró: …» con la marca coincide / revisar. Las preguntas de las reglas llenan los códigos
// (v_ref1, v_ref2) y las gestiones se ven dentro de cada vinculación. Se guardan las mismas columnas de siempre más las del V250; nada de esto cambia el Word.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const hoyIso = () => fechaAIso(new Date())

/** Los valores de una vinculación (por clave) convertidos a los de la tabla (por id de columna). */
export function valoresPorId(seccion, porClave) {
  const r = {}
  Object.entries(porClave).forEach(([clave, valor]) => {
    const id = seccion.columnas.find(c => c.clave === clave)?.idColumna
    if (id !== undefined) r[id] = valor
  })
  return r
}

/** Experiencias declaradas dentro del periodo que todavía no tienen su vinculación. Una fila que alguien creó a mano con la misma empresa cuenta como existente. */
export function experienciasSinVinculacion(seccion, experiencias, desde, hasta, yaCreadas) {
  const id = (clave) => seccion.columnas.find(c => c.clave === clave)?.idColumna
  const conOrigen = new Set(seccion.filas.map(f => String(f.valores[id('v_id_exp')] ?? '')).filter(Boolean))
  const manuales = seccion.filas.filter(f => !tiene(f.valores[id('v_id_exp')])).map(f => f.valores[id('v_empresa')])
  return experiencias.filter(e => !conOrigen.has(String(e.id)) && !yaCreadas.has(e.id) && !manuales.some(m => iguales(m, e.empresa)) && dentroDelPeriodo(e, desde, hasta))
}

export function Marca({ r, texto }) {
  if (!r) return null
  const ok = r.nivel === 'coincide'
  return (
    <span className={`inline-block rounded px-1.5 py-0.5 text-[11px] font-semibold ${ok ? 'bg-green-100 text-green-800' : 'bg-orange-100 text-orange-900'}`}>
      {ok ? '✓ coincide' : '⚠ revisar'}{texto ? ` · ${texto}` : ''}
    </span>
  )
}

export function Declaro({ valor, marca, extra }) {
  return (
    <p className="mt-1 text-xs text-gray-600 flex flex-wrap items-center gap-x-2 gap-y-1">
      <span>Declaró: <b className="text-gray-800">{tiene(valor) ? valor : '—'}</b></span>
      {marca}
      {extra}
    </p>
  )
}

export function Fila({ etiqueta, children, clave }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-[190px_minmax(0,1fr)] border-b border-gray-200" data-clave={clave}>
      <div className="bg-gray-100 px-2 py-2 text-xs font-semibold text-gray-800 sm:border-r border-gray-200">{etiqueta}</div>
      <div className="px-2 py-2 min-w-0">{children}</div>
    </div>
  )
}

export function Opciones({ etiqueta, opciones, valor, onElegir, deshabilitado }) {
  return (
    <div role="radiogroup" aria-label={etiqueta} className="flex flex-wrap gap-2">
      {opciones.map(o => {
        const { v, texto, off } = typeof o === 'string' ? { v: o, texto: o } : o
        const activo = valor === v
        return (
          <button key={v} type="button" role="radio" aria-checked={activo} disabled={deshabilitado || off} onClick={() => onElegir(activo ? '' : v)}
            className={`min-h-11 px-3 text-sm font-medium rounded-lg border ${activo ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'} disabled:opacity-50`}>
            {texto ?? v}
          </button>
        )
      })}
    </div>
  )
}

export default function VinculacionesLaboral({ secciones, experiencias, periodo, objetivo, soloLectura, idServicioSubproceso, onCelda, onAgregar, onQuitar, onError }) {
  const sv = secciones.find(s => s.clave === 'vinculaciones')
  const sg = secciones.find(s => s.clave === 'gestiones')
  const [indice, setIndice] = useState(0)
  const [noHabiles, setNoHabiles] = useState(new Set())

  const colV = (clave) => sv?.columnas.find(c => c.clave === clave)
  const colG = (clave) => sg?.columnas.find(c => c.clave === clave)
  const valoresDe = (fila) => Object.fromEntries((sv?.columnas ?? []).map(c => [c.clave, fila.valores[c.idColumna] ?? '']))
  const filas = sv?.filas ?? []
  const i = Math.min(indice, Math.max(0, filas.length - 1))
  const fila = filas[i]
  const v = fila ? valoresDe(fila) : null

  // Un faltante de «Revisión y envío» trae la vinculación exacta (`vinc-N:campo`): se abre esa pestaña antes de que la pantalla busque la celda.
  useEffect(() => {
    const m = /^vinc-(\d+):/.exec(objetivo?.clave ?? '')
    if (m) setIndice(Math.max(0, Number(m[1]) - 1))
  }, [objetivo])
  useEffect(() => { if (indice > filas.length - 1) setIndice(Math.max(0, filas.length - 1)) }, [filas.length, indice])

  const poner = (clave, valor) => { const c = colV(clave); if (c && fila) onCelda(sv.idSeccion, fila.localId, c.idColumna, valor) }
  const ponerVarios = (obj) => Object.entries(obj).forEach(([k, x]) => poner(k, x))

  const exp = useMemo(() => (v && tiene(v.v_id_exp) ? experiencias.find(e => String(e.id) === String(v.v_id_exp)) : null), [v, experiencias])
  const fuera = !!v && tiene(v.v_id_exp) && !!exp && !dentroDelPeriodo(exp, periodo.desde, periodo.hasta)
  const noDeclarada = v?.v_no_declarada === 'Sí'
  const cambio = !!v && cambioLaDeclaracion(v, exp)
  const lleva = v && tiene(v.v_id_exp)

  const gestiones = useMemo(() => (sg && fila ? sg.filas.filter(g => String(g.valores[colG('g_vinculacion')?.idColumna] ?? '') === String(i + 1)) : []),
    [sg, fila, i]) // eslint-disable-line react-hooks/exhaustive-deps
  const llamadasHechas = gestiones.filter(g => iguales(g.valores[colG('g_medio')?.idColumna], 'Llamada')).length

  const agregarHallada = () => {
    onAgregar(sv.idSeccion, valoresPorId(sv, { v_no_declarada: 'Sí', v_tipo: 'Empresa', v_ref1: '—', v_ref2: '—' }))
    setIndice(filas.length)
  }

  const registrarGestion = () => {
    const medio = llamadasHechas < LLAMADAS_OBLIGATORIAS ? 'Llamada' : ''
    onAgregar(sg.idSeccion, valoresPorId(sg, { g_vinculacion: String(i + 1), g_fecha: hoyIso(), g_medio: medio }))
  }

  const solicitarCertificado = async () => {
    const hoy = new Date()
    let festivos = noHabiles
    try {
      const fin = new Date(hoy); fin.setDate(fin.getDate() + 30)
      festivos = new Set((await agendaService.diasNoHabiles(fechaAIso(hoy), fechaAIso(fin))).map(d => d.fecha))
      setNoHabiles(festivos)
    } catch { onError?.('No se pudieron leer los festivos: la fecha límite cuenta solo sábados y domingos como no hábiles.') }
    ponerVarios({ v_fecha_sol_eval: fechaAIso(hoy), v_fecha_limite: fechaAIso(fechaLimite(hoy, festivos)) })
  }

  const alternarCodigo = (codigo, marcar) => {
    const nuevo = conCodigo(v, codigo, marcar)
    if (!nuevo) { onError?.('Ya hay dos códigos en esta vinculación. Quita uno antes de agregar otro.'); return }
    ponerVarios(nuevo)
  }

  if (!sv) return <p className="text-gray-700">Este formato no tiene la sección de vinculaciones.</p>

  const preguntas = v ? preguntasDeCodigo(v) : []
  const sugerido = v ? codigosSugeridos(v) : { codigos: [], razones: [] }
  const vigente = !!v && iguales(v.v_fecha_retiro, ACTUAL)
  const sabana = v?.v_validado_por === SABANA
  const persona = v?.v_tipo === PERSONA_NATURAL
  const faltanLlamadas = v ? llamadasQueFaltan(v, llamadasHechas) : 0
  const limite = v && tiene(v.v_fecha_limite) ? isoAFecha(v.v_fecha_limite) : null
  const quedan = limite ? diasEntre(new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate()), limite) : null
  const clasLlamados = v ? claseDeLlamados(v.v_llamados) : ''
  const salarioDif = v && tiene(v.v_salario) && tiene(v.v_decl_salario) && soloDigitos(v.v_salario) !== soloDigitos(v.v_decl_salario)

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label="Vinculaciones">
        {filas.map((f, n) => {
          const empresa = f.valores[colV('v_empresa')?.idColumna]
          return (
            <button key={f.localId} type="button" role="tab" aria-selected={n === i} onClick={() => setIndice(n)}
              className={`min-h-11 px-3 text-sm rounded-lg border max-w-[16rem] truncate ${n === i ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800 hover:bg-gray-50'}`}>
              N.º {n + 1}{tiene(empresa) ? ` · ${empresa}` : ''}
            </button>
          )
        })}
        {!soloLectura && (
          <button type="button" onClick={agregarHallada} className="min-h-11 px-3 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar vinculación hallada</button>
        )}
      </div>

      {filas.length === 0 && (
        <p className="text-sm text-gray-700">
          {tiene(periodo.desde) && tiene(periodo.hasta)
            ? 'No hay vinculaciones: el evaluado no declaró experiencia dentro del periodo validado. Si encuentras una (por ejemplo en la sábana pensional), agrégala.'
            : 'Elige el periodo validado en «Alcance y experiencia declarada» para crear las vinculaciones desde lo que declaró el evaluado.'}
        </p>
      )}

      {fila && v && (
        <>
          <div className="flex flex-wrap gap-2 text-xs">
            {noDeclarada && <span className="rounded bg-purple-100 text-purple-900 px-2 py-1 font-semibold">No declarada en la hoja de vida</span>}
            {fuera && <span className="rounded bg-gray-200 text-gray-800 px-2 py-1 font-semibold">Fuera del periodo</span>}
          </div>
          {cambio && <p role="alert" className="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">El evaluado cambió su declaración después de crearse esta vinculación. Lo declarado que ves abajo es lo que había al crearla.</p>}
          {fuera && <p className="text-sm text-gray-700">Esta experiencia quedó fuera del periodo validado. No se borra sola; si ya no hace parte del estudio, déjala sin diligenciar.</p>}

          <section aria-label={`Vinculación laboral N.º ${i + 1}`} className="border border-gray-300 rounded">
            <h3 className="bg-primary-700 text-white text-sm font-bold px-3 py-2 rounded-t uppercase tracking-wide" style={{ background: '#7b1f2b' }}>Vinculación laboral N.º {i + 1}</h3>

            <Fila etiqueta="Empresa" clave="v_empresa">
              <input aria-label="Empresa" className={inputCls} value={v.v_empresa} disabled={soloLectura} onChange={e => poner('v_empresa', e.target.value)} />
              {lleva && <Declaro valor={v.v_decl_empresa} marca={<Marca r={tiene(v.v_empresa) ? { nivel: iguales(v.v_empresa, v.v_decl_empresa) ? 'coincide' : 'revisar' } : null} />} />}
            </Fila>
            <Fila etiqueta="Ciudad" clave="v_ciudad">
              <input aria-label="Ciudad" className={inputCls} value={v.v_ciudad} disabled={soloLectura} onChange={e => poner('v_ciudad', e.target.value)} />
              {lleva && <Declaro valor={v.v_decl_ciudad} marca={<Marca r={tiene(v.v_ciudad) && tiene(v.v_decl_ciudad) ? { nivel: iguales(v.v_ciudad, v.v_decl_ciudad) ? 'coincide' : 'revisar' } : null} />} />}
            </Fila>
            <Fila etiqueta="Fecha de inicio" clave="v_fecha_inicio">
              <CampoFecha aria-label="Fecha de inicio confirmada" value={textoAIso(v.v_fecha_inicio)} disabled={soloLectura} onChange={iso => poner('v_fecha_inicio', isoATexto(iso))} />
              {lleva && (() => { const r = compararFecha(v.v_fecha_inicio, v.v_decl_inicio); return <Declaro valor={isoATexto(v.v_decl_inicio)} marca={<Marca r={r} texto={r && r.dias > 0 ? `Diferencia: ${r.dias} días · ${r.nivel === 'fondo' ? 'de fondo' : 'informativo'}` : null} />} /> })()}
            </Fila>
            <Fila etiqueta="Fecha de retiro" clave="v_fecha_retiro">
              <div className="flex flex-wrap gap-2 items-start">
                <div className="flex-1 min-w-[10rem]">
                  <CampoFecha aria-label="Fecha de retiro confirmada" value={textoAIso(v.v_fecha_retiro)} disabled={soloLectura || vigente} onChange={iso => poner('v_fecha_retiro', isoATexto(iso))} />
                </div>
                <button type="button" aria-pressed={vigente} disabled={soloLectura}
                  onClick={() => (vigente ? ponerVarios({ v_fecha_retiro: '', ...(v.v_motivo_clase === 'Vigente (aún vinculado)' ? { v_motivo_clase: '' } : {}) }) : ponerVarios({ v_fecha_retiro: ACTUAL, v_motivo_clase: 'Vigente (aún vinculado)', v_motivo: '' }))}
                  className={`min-h-11 px-3 text-sm font-medium rounded-lg border ${vigente ? 'border-primary-600 bg-primary-600 text-white' : 'border-gray-300 bg-white text-gray-800'}`}>Sigue vinculado</button>
              </div>
              {lleva && (() => { const r = compararRetiro(v.v_fecha_retiro, v.v_decl_fin); return <Declaro valor={tiene(v.v_decl_fin) ? isoATexto(v.v_decl_fin) : (exp && /^s[ií]$/i.test(String(exp.labora_actualmente)) ? ACTUAL : '')} marca={<Marca r={r} texto={r && r.dias > 0 ? `Diferencia: ${r.dias} días · ${r.nivel === 'fondo' ? 'de fondo' : 'informativo'}` : null} />} /> })()}
            </Fila>
            <Fila etiqueta="Tipo de contrato" clave="v_tipo_contrato">
              <input aria-label="Tipo de contrato" className={inputCls} value={v.v_tipo_contrato} disabled={soloLectura} onChange={e => poner('v_tipo_contrato', e.target.value)} />
              {lleva && <Declaro valor={v.v_decl_contrato} marca={<Marca r={tiene(v.v_tipo_contrato) && tiene(v.v_decl_contrato) ? { nivel: iguales(v.v_tipo_contrato, v.v_decl_contrato) ? 'coincide' : 'revisar' } : null} />} />}
            </Fila>
            <Fila etiqueta="Cargo desempeñado" clave="v_cargo">
              <input aria-label="Cargo desempeñado" className={inputCls} value={v.v_cargo} disabled={soloLectura} onChange={e => poner('v_cargo', e.target.value)} />
              {lleva && <Declaro valor={v.v_decl_cargo} marca={<Marca r={tiene(v.v_cargo) ? { nivel: iguales(v.v_cargo, v.v_decl_cargo) ? 'coincide' : 'revisar' } : null} />} />}
            </Fila>
            <Fila etiqueta="Salario devengado" clave="v_salario">
              <input aria-label="Salario devengado" inputMode="numeric" placeholder="$0" className={inputCls} disabled={soloLectura}
                value={aMoneda(soloDigitos(v.v_salario))} onChange={e => poner('v_salario', aMoneda(soloDigitos(e.target.value)))} />
              {lleva && <Declaro valor={aMoneda(soloDigitos(v.v_decl_salario))} marca={<Marca r={tiene(v.v_salario) && tiene(v.v_decl_salario) ? { nivel: salarioDif ? 'revisar' : 'coincide' } : null} />}
                extra={<span className="text-gray-600">No suma puntos{salarioDif ? ' · si difiere, regístralo como hallazgo sin impacto' : ''}</span>} />}
            </Fila>

            {!sabana && (
              <Fila etiqueta="Llamados de atención" clave="v_llamados">
                <Opciones etiqueta="Llamados de atención" opciones={LLAMADOS} valor={clasLlamados} deshabilitado={soloLectura}
                  onElegir={c => poner('v_llamados', armarLlamados(c, detalleDeLlamados(v.v_llamados)))} />
                {clasLlamados && clasLlamados !== 'Ninguno' && (
                  <div data-clave="v_llamados_detalle">
                    <label className="block text-xs font-semibold text-gray-700 mt-2 mb-1" htmlFor={`llam-${fila.localId}`}>Detalle del llamado (obligatorio)</label>
                    <AreaTexto id={`llam-${fila.localId}`} max={colV('v_llamados')?.longitudMaxima} value={detalleDeLlamados(v.v_llamados)} disabled={soloLectura}
                      onChange={x => poner('v_llamados', armarLlamados(clasLlamados, x))} />
                    {!tiene(detalleDeLlamados(v.v_llamados)) && <p className="text-xs text-red-700">Escribe el detalle.</p>}
                  </div>
                )}
                {lleva && <Declaro valor={v.v_decl_llamados} marca={<Marca r={clasLlamados && claseDeLlamados(v.v_decl_llamados) ? { nivel: clasLlamados === claseDeLlamados(v.v_decl_llamados) ? 'coincide' : 'revisar' } : null} />} />}
              </Fila>
            )}
            {!sabana && (
              <Fila etiqueta="Motivo del retiro" clave="v_motivo">
                {vigente ? <p className="text-sm text-gray-700">No aplica: labora actualmente.</p> : (
                  <>
                    <AreaTexto ariaLabel="Motivo del retiro (lo que informa la empresa)" max={colV('v_motivo')?.longitudMaxima} value={v.v_motivo} disabled={soloLectura}
                      onChange={x => poner('v_motivo', x)} />
                    <p className="text-xs font-semibold text-gray-700 mt-2 mb-1">Clasificación (no se imprime)</p>
                    <Opciones etiqueta="Clasificación del motivo" valor={v.v_motivo_clase} deshabilitado={soloLectura} onElegir={c => poner('v_motivo_clase', c)}
                      opciones={(colV('v_motivo_clase')?.opciones ?? []).filter(o => o && o !== '—' && o !== 'Vigente (aún vinculado)')} />
                  </>
                )}
                {lleva && !vigente && <Declaro valor={v.v_decl_motivo} marca={<Marca r={v.v_motivo_clase && claseDeMotivo(v.v_decl_motivo) ? { nivel: v.v_motivo_clase === claseDeMotivo(v.v_decl_motivo) ? 'coincide' : 'revisar' } : null} />} />}
              </Fila>
            )}

            <Fila etiqueta="Contacto en la empresa" clave="v_contacto">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <input aria-label="Nombre del contacto" placeholder="Nombre" className={inputCls} value={v.v_contacto} disabled={soloLectura} onChange={e => poner('v_contacto', e.target.value)} />
                <input aria-label="Cargo del contacto" placeholder="Cargo (área administrativa, nómina o talento humano)" className={inputCls} value={v.v_contacto_cargo} disabled={soloLectura} onChange={e => poner('v_contacto_cargo', e.target.value)} />
              </div>
            </Fila>
            <Fila etiqueta="Teléfono de la empresa" clave="v_telefono">
              <input aria-label="Teléfono de la empresa" className={inputCls} value={v.v_telefono} disabled={soloLectura} onChange={e => poner('v_telefono', e.target.value)} />
              {lleva && <Declaro valor={v.v_decl_telefono} marca={<Marca r={tiene(v.v_telefono) && tiene(v.v_decl_telefono) ? { nivel: soloDigitos(v.v_telefono) === soloDigitos(v.v_decl_telefono) ? 'coincide' : 'revisar' } : null} />} />}
            </Fila>
            <Fila etiqueta="Estado actual de la empresa (RUES)" clave="v_estado_empresa">
              <Opciones etiqueta="Estado de la empresa" opciones={['Activa', 'Cancelada', 'En liquidación']} valor={v.v_estado_empresa} deshabilitado={soloLectura} onElegir={x => poner('v_estado_empresa', x)} />
            </Fila>
            <Fila etiqueta={<span className="flex flex-wrap items-center gap-1">Tipo de vinculación <NoImprime /></span>} clave="v_tipo">
              <Opciones etiqueta="Tipo de vinculación" opciones={[{ v: 'Empresa', texto: 'Empresa formal' }, { v: PERSONA_NATURAL, texto: 'Persona natural o informal' }]} valor={v.v_tipo} deshabilitado={soloLectura}
                onElegir={x => ponerVarios({ v_tipo: x || 'Empresa', ...(x !== PERSONA_NATURAL && v.v_validado_por === JEFE ? { v_validado_por: '' } : {}) })} />
            </Fila>
            <Fila etiqueta="Validado por" clave="v_validado_por">
              <Opciones etiqueta="Validado por" valor={v.v_validado_por} deshabilitado={soloLectura} onElegir={x => poner('v_validado_por', x)}
                opciones={[{ v: EMPRESA, texto: 'La empresa' }, { v: JEFE, texto: 'Jefe inmediato', off: !persona }, { v: SABANA, texto: 'Sábana pensional' }, { v: PENDIENTE, texto: 'Pendiente' }]} />
              {!persona && <p className="text-xs text-gray-600 mt-1">La empresa se valida con el área administrativa, nómina o talento humano, nunca con el jefe. «Jefe inmediato» solo se habilita para persona natural o informal.</p>}
              {sabana && <p className="mt-2 text-sm bg-gray-50 border border-gray-200 rounded p-2">{v.v_nota || 'Se confirma la vinculación por medio de bases de datos públicas y privadas. No fue posible validar motivo de retiro, llamados de atención ni la veracidad de la información.'}</p>}
            </Fila>
            <Fila etiqueta="Observaciones" clave="v_observaciones">
              <AreaTexto ariaLabel="Observaciones" rows={3} max={colV('v_observaciones')?.longitudMaxima} value={v.v_observaciones} disabled={soloLectura} onChange={x => poner('v_observaciones', x)} />
              {!soloLectura && !tiene(v.v_observaciones) && tiene(v.v_validado_por) && tiene(v.v_fecha_inicio) && (
                <button type="button" className="mt-1 min-h-11 text-sm underline text-primary-700" onClick={() => poner('v_observaciones', borradorObservacion(v))}>Sugerir un borrador con lo confirmado</button>
              )}
            </Fila>
            <Fila etiqueta={<span className="flex flex-wrap items-center gap-1">Nota de seguimiento <NoImprime /></span>} clave="v_seguimiento">
              <div className="rounded border border-green-300 bg-green-50 p-2">
                <AreaTexto ariaLabel="Nota de seguimiento (no se imprime)" max={colV('v_seguimiento')?.longitudMaxima} value={v.v_seguimiento} disabled={soloLectura} onChange={x => poner('v_seguimiento', x)}
                  className="w-full min-h-11 border border-green-300 rounded-lg px-3 py-2 text-sm bg-white disabled:bg-gray-100" />
                <p className="text-xs text-green-900 mt-1">Para ti y para quien coordina: qué se ha intentado, a quién falta llamar.</p>
              </div>
            </Fila>
            <Fila etiqueta="Calificación" clave="v_ref1">
              <p className="text-sm text-gray-900">{[v.v_ref1, v.v_ref2].filter(x => tiene(x) && x !== '—').join(' · ') || '—'}</p>
              {sugerido.codigos.length > 0
                ? <p className="text-xs text-gray-700 mt-1">Sugerido: <b>{sugerido.codigos.join(', ')}</b> · {sugerido.razones.map(r => r.replace(/^[A-Z-0-9]+: /, '')).join('; ')}. Tú confirmas en las preguntas de abajo.</p>
                : <p className="text-xs text-gray-600 mt-1">Sin sugerencia por ahora.</p>}
            </Fila>
          </section>

          {/* Preguntas para el código */}
          <section aria-labelledby="lb-preg" className="border border-gray-200 rounded p-3 bg-gray-50">
            <div className="flex items-center justify-between gap-2 mb-2">
              <h3 id="lb-preg" className="text-sm font-bold text-gray-900">Preguntas para el código</h3>
              <NoImprime />
            </div>
            <p className="text-xs text-gray-700 mb-2">Máximo dos códigos por vinculación. El sistema sugiere; tú decides.</p>
            <ul className="space-y-3">
              {preguntas.map(p => {
                const si = tieneCodigo(v, p.codigo)
                return (
                  <li key={p.id} className={p.aplica ? '' : 'opacity-60'} data-clave={p.id === 'falso' ? 'v_lab01_base' : undefined}>
                    <p className="text-sm text-gray-900"><b>{p.codigo}</b> · {p.pregunta}{p.sugerido && !si && <span className="ml-2 text-xs rounded bg-amber-100 text-amber-900 px-1.5 py-0.5">Sugerido: Sí · {p.porque}</span>}</p>
                    {p.aplica
                      ? <Opciones etiqueta={`${p.codigo}`} opciones={[{ v: 'No', texto: 'No' }, { v: 'Sí', texto: 'Sí' }]} valor={si ? 'Sí' : 'No'} deshabilitado={soloLectura} onElegir={x => alternarCodigo(p.codigo, x === 'Sí')} />
                      : <p className="text-xs text-gray-600">{p.razon}</p>}
                    {p.codigo === 'LAB-01' && si && (
                      <div className="mt-2 space-y-2">
                        <Opciones etiqueta="Sustento del LAB-01" valor={v.v_lab01_base} deshabilitado={soloLectura} onElegir={x => poner('v_lab01_base', x)} opciones={(colV('v_lab01_base')?.opciones ?? []).filter(Boolean)} />
                        {v.v_lab01_base === 'Alteración evidente en inspección visual' && (
                          <div data-clave="v_vobo">
                            <label className="block text-xs font-semibold text-gray-700 mb-1" htmlFor={`vobo-${fila.localId}`}>Visto bueno del segundo analista (nombre)</label>
                            <input id={`vobo-${fila.localId}`} className={inputCls} value={v.v_vobo} disabled={soloLectura} onChange={e => poner('v_vobo', e.target.value)} />
                          </div>
                        )}
                      </div>
                    )}
                    {p.codigo === 'LAB-11' && si && !tiene(v.v_fecha_sol_eval) && <p className="text-xs text-red-700 mt-1">Con LAB-11 hace falta la fecha de solicitud del certificado: usa «Solicitar certificado al evaluado».</p>}
                  </li>
                )
              })}
              {noDeclarada && (
                <li data-clave="v_mas_30">
                  <p className="text-sm text-gray-900">¿Esta vinculación hallada dura más de 30 días?</p>
                  <Opciones etiqueta="Dura más de 30 días" opciones={['Sí', 'No']} valor={v.v_mas_30} deshabilitado={soloLectura} onElegir={x => poner('v_mas_30', x)} />
                </li>
              )}
            </ul>
          </section>

          {/* Gestiones de esta vinculación */}
          <section aria-labelledby="lb-gest" data-clave="lb-gestiones" className="border border-gray-200 rounded p-3">
            <div className="flex items-center justify-between gap-2 mb-2">
              <h3 id="lb-gest" className="text-sm font-bold text-gray-900">Gestiones de esta vinculación</h3>
              <NoImprime />
            </div>
            {faltanLlamadas > 0 && <p role="alert" className="text-sm text-red-800 bg-red-50 border border-red-200 rounded p-2 mb-2">Faltan {faltanLlamadas} {faltanLlamadas === 1 ? 'llamada obligatoria' : 'llamadas obligatorias'} ({llamadasHechas} de {LLAMADAS_OBLIGATORIAS} registradas).</p>}
            {gestiones.length === 0 && <p className="text-sm text-gray-700 mb-2">Sin gestiones registradas.</p>}
            <GestionesTabla seccion={sg} filas={gestiones} soloLectura={soloLectura} onCelda={onCelda} onQuitar={onQuitar} />
            {!soloLectura && <button type="button" onClick={registrarGestion} className="mt-2 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Registrar gestión</button>}

            {vigente && v.v_validado_por === EMPRESA && (
              <div className="mt-3" data-clave="v_autorizacion">
                <p className="text-sm text-gray-900">Autorización del evaluado para contactar a la empresa actual</p>
                <Opciones etiqueta="Autorización del evaluado" opciones={['Sí', 'No']} valor={v.v_autorizacion} deshabilitado={soloLectura} onElegir={x => poner('v_autorizacion', x)} />
                {v.v_autorizacion === 'No' && <p className="text-xs text-amber-900 mt-1">Sin autorización no se contacta a la empresa: valida por sábana pensional o aportes.</p>}
              </div>
            )}

            <div className="mt-3" data-clave="v_fecha_sol_eval">
              {!soloLectura && <button type="button" onClick={solicitarCertificado} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Solicitar certificado al evaluado</button>}
              {limite && (
                <p className={`mt-2 text-sm ${quedan < 0 ? 'text-red-800' : 'text-gray-800'}`}>
                  Solicitado el {isoATexto(v.v_fecha_sol_eval)} · fecha límite: <b>{fechaATexto(limite)}</b> (4 días hábiles) ·{' '}
                  {quedan > 0 ? `quedan ${quedan} ${quedan === 1 ? 'día' : 'días'}` : quedan === 0 ? 'vence hoy' : `venció hace ${-quedan} ${-quedan === 1 ? 'día' : 'días'}`}
                </p>
              )}
              {limite && quedan < 0 && v.v_validado_por !== PENDIENTE && <p role="alert" className="text-sm text-red-800 mt-1">Venció el plazo del evaluado. Si no aportó el certificado, marca la vinculación como «Pendiente» y responde LAB-11.</p>}
            </div>

            <div className="mt-3" data-clave="v_soporte">
              <p className="text-sm font-semibold text-gray-900 mb-1 flex flex-wrap items-center gap-2">Soporte de la vinculación <NoImprime /></p>
              <p className="text-xs text-gray-700 mb-1">El certificado, la sábana pensional o la constancia de la llamada. Es obligatorio para cerrar.</p>
              {!tiene(v.v_soporte) || v.v_soporte === '[]' ? <p className="text-xs font-semibold text-red-700">Falta soporte</p> : <p className="text-xs font-semibold text-green-800">✓ Soporte cargado</p>}
              <InputPorTipo tipoDato="ARCHIVO" value={v.v_soporte} onChange={x => poner('v_soporte', x)} idServicioSubproceso={idServicioSubproceso} onError={onError} disabled={soloLectura} />
            </div>
          </section>

          {!soloLectura && fila.idFila == null && (
            <button type="button" onClick={() => onQuitar(sv.idSeccion, fila.localId)} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Quitar esta vinculación</button>
          )}
        </>
      )}
    </div>
  )
}
