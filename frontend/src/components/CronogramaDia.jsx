import { useState } from 'react'
import { hoyLocalDateString } from '../utils/fechas'

// Cronograma tipo turnos de hospital / agenda de calendario, con dos formas de verlo (vertical:
// horas de arriba a abajo, empleados en columnas — u horizontal: horas en columnas, empleados en
// filas) y zoom sobre el eje del tiempo: al acercar no solo se agranda, se ven también los
// minutos (30 min, luego 15 min), no solo la hora en punto. Mismo rango horario que usa el
// backend para las franjas disponibles (AsignacionService.JORNADA_INICIO/JORNADA_FIN).
const JORNADA_INICIO_H = 8
const JORNADA_FIN_H = 17
const TOTAL_MIN = (JORNADA_FIN_H - JORNADA_INICIO_H) * 60

const ANCHO_COL_HORA = 56          // vertical: ancho de la columna de horas
const ANCHO_COL_EMPLEADO_V = 200   // vertical: ancho de cada columna de empleado
const ANCHO_COL_EMPLEADO_H = 160   // horizontal: ancho de la columna de empleados
const ALTO_HEADER = 52
const ALTO_CARRIL = 60             // horizontal: alto de cada "carril" cuando hay choques
const ALTO_MAX_VISIBLE = 620       // alto máximo del cronograma antes de scrollear verticalmente

// Niveles de zoom: acercar no solo agranda, cambia el intervalo de las marcas de tiempo — de solo
// horas en punto a cada 30 y luego cada 15 minutos.
const NIVELES_ZOOM = [
  { id: 'h1',  etiqueta: '1 h',    intervaloMin: 60, pxPorMinuto: 64 / 60 },
  { id: 'm30', etiqueta: '30 min', intervaloMin: 30, pxPorMinuto: 100 / 60 },
  { id: 'm15', etiqueta: '15 min', intervaloMin: 15, pxPorMinuto: 168 / 60 },
]

// Duración mínima "visual" (no la real) que ocupa un bloque para efectos de layout — evita que
// subprocesos muy cortos (ej. Antecedentes, 15 min) queden ilegibles. Cuando dos bloques del mismo
// empleado chocan por esto (aunque su hora real no se cruce), se reparten en carriles paralelos en
// vez de superponerse — como hace cualquier calendario real.
const DURACION_MIN_VISUAL = 30

const ESTILO_ESTADO = {
  ASIGNADO:   { borde_l: 'border-l-blue-500',    punto: 'bg-blue-500',    texto: 'text-blue-800',    fondo: 'bg-blue-50',    fondoHover: 'hover:bg-blue-100/80' },
  COMPLETADO: { borde_l: 'border-l-emerald-500', punto: 'bg-emerald-500', texto: 'text-emerald-800', fondo: 'bg-emerald-50', fondoHover: 'hover:bg-emerald-100/80' },
  CANCELADO:  { borde_l: 'border-l-red-400',     punto: 'bg-red-400',     texto: 'text-red-700 line-through decoration-red-300', fondo: 'bg-red-50', fondoHover: 'hover:bg-red-100/70' },
}
const ESTILO_DEFAULT = { borde_l: 'border-l-gray-400', punto: 'bg-gray-400', texto: 'text-gray-700', fondo: 'bg-gray-50', fondoHover: 'hover:bg-gray-100' }

function minutosDesdeJornada(iso) {
  const d = new Date(iso)
  return (d.getHours() * 60 + d.getMinutes()) - JORNADA_INICIO_H * 60
}

function iniciales(nombre) {
  const partes = (nombre ?? '').trim().split(/\s+/).filter(Boolean)
  const a = partes[0]?.[0] ?? ''
  const b = partes[1]?.[0] ?? ''
  return (a + b).toUpperCase() || '—'
}

function generarMarcas(intervaloMin) {
  const marcas = []
  for (let t = 0; t <= TOTAL_MIN; t += intervaloMin) {
    const hora = JORNADA_INICIO_H + Math.floor(t / 60)
    const minuto = t % 60
    marcas.push({ t, label: `${String(hora).padStart(2, '0')}:${String(minuto).padStart(2, '0')}`, enPunto: minuto === 0 })
  }
  return marcas
}

/** Reparte en carriles paralelos los bloques de un mismo empleado que REALMENTE chocan en su
 *  horario — nunca separa en columnas algo que en la realidad no se cruza. La duración "visual"
 *  (para que subprocesos cortos como "Antecedentes" de 15 min no queden ilegibles) se infla
 *  después, pero solo hasta donde alcance el hueco real antes del siguiente bloque de su mismo
 *  carril — así nunca termina pisándolo ni inventa un choque que no existe. */
function empaquetarCarriles(items) {
  const conRango = items
    .map(a => {
      const inicio = Math.max(0, Math.min(TOTAL_MIN, minutosDesdeJornada(a.fechaProgramada)))
      const real = Math.max(1, Math.min(TOTAL_MIN - inicio, a.duracionMinutos ?? 60))
      return { ...a, _inicio: inicio, _duracionReal: real, _fin: inicio + real }
    })
    .sort((a, b) => a._inicio - b._inicio || a._fin - b._fin)

  // 1) Carriles según el horario real — dos bloques pegados sin hueco (fin de uno = inicio del
  //    siguiente) van en el MISMO carril, porque de verdad no chocan.
  const finPorCarril = [] // minuto en que queda libre cada carril
  conRango.forEach(item => {
    let carril = finPorCarril.findIndex(fin => fin <= item._inicio)
    if (carril === -1) { carril = finPorCarril.length; finPorCarril.push(0) }
    finPorCarril[carril] = item._fin
    item._carril = carril
  })
  const numCarriles = Math.max(1, finPorCarril.length)

  // 2) Duración visual: se infla para legibilidad, acotada al hueco real disponible antes del
  //    siguiente bloque de su mismo carril (o el fin de la jornada si es el último).
  const porCarril = new Map()
  conRango.forEach(item => {
    if (!porCarril.has(item._carril)) porCarril.set(item._carril, [])
    porCarril.get(item._carril).push(item)
  })
  porCarril.forEach(itemsDelCarril => {
    itemsDelCarril.forEach((item, i) => {
      const limite = itemsDelCarril[i + 1]?._inicio ?? TOTAL_MIN
      const deseado = item._inicio + Math.max(item._duracionReal, DURACION_MIN_VISUAL)
      item._duracionVisual = Math.max(item._duracionReal, Math.min(deseado, limite) - item._inicio)
    })
  })

  return { items: conRango, numCarriles }
}

function ControlZoom({ nivel, onCambiar }) {
  const idx = NIVELES_ZOOM.findIndex(n => n.id === nivel.id)
  return (
    <div className="flex items-center gap-1.5">
      <button type="button" onClick={() => onCambiar(NIVELES_ZOOM[idx - 1])} disabled={idx === 0}
        title="Ver menos detalle"
        className="w-6 h-6 flex items-center justify-center rounded-md border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-30 disabled:hover:bg-transparent transition-colors text-sm font-semibold leading-none">
        −
      </button>
      <span className="text-[11px] text-gray-500 font-medium min-w-[62px] text-center select-none">
        Cada {nivel.etiqueta}
      </span>
      <button type="button" onClick={() => onCambiar(NIVELES_ZOOM[idx + 1])} disabled={idx === NIVELES_ZOOM.length - 1}
        title="Ver más detalle (minutos)"
        className="w-6 h-6 flex items-center justify-center rounded-md border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-30 disabled:hover:bg-transparent transition-colors text-sm font-semibold leading-none">
        +
      </button>
    </div>
  )
}

function Leyenda({ mostrarLineaActual }) {
  return (
    <div className="flex items-center gap-4 px-4 py-2.5 border-t border-gray-100 bg-gray-50/50 text-[11px] text-gray-500">
      <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500" /> Asignado</span>
      <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-emerald-500" /> Completado</span>
      <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-red-400" /> Cancelado</span>
      {mostrarLineaActual && (
        <span className="flex items-center gap-1.5 ml-auto"><span className="w-2 h-0.5 bg-red-500" /> Hora actual</span>
      )}
    </div>
  )
}

/* ─── Vista vertical: horas de arriba a abajo, empleados en columnas lado a lado ─── */
function VistaVertical({ empleados, marcas, pxPorMinuto, onSeleccionar, mostrarLineaActual, minutoActual }) {
  const largoTotal = TOTAL_MIN * pxPorMinuto

  return (
    <div className="relative flex" style={{ minWidth: `${ANCHO_COL_HORA + empleados.length * ANCHO_COL_EMPLEADO_V}px` }}>
      <div className="flex-shrink-0 sticky left-0 z-20 bg-white" style={{ width: `${ANCHO_COL_HORA}px` }}>
        <div className="border-b border-gray-200 bg-gray-50/90 sticky top-0 z-20" style={{ height: `${ALTO_HEADER}px` }} />
        <div className="relative" style={{ height: `${largoTotal}px` }}>
          {marcas.map((m, i) => (
            <div key={m.t}
              className={`absolute left-0 right-2 text-right ${i === 0 ? '' : '-translate-y-1/2'} ${m.enPunto ? 'text-[11px] font-medium text-gray-400' : 'text-[10px] text-gray-300'}`}
              style={{ top: `${m.t * pxPorMinuto}px` }}>
              {m.label}
            </div>
          ))}
        </div>
      </div>

      {empleados.map(([key, { nombre, items }]) => {
        const { items: itemsCarril, numCarriles } = empaquetarCarriles(items)
        return (
          <div key={key} className="flex-shrink-0 border-l border-gray-100" style={{ width: `${ANCHO_COL_EMPLEADO_V}px` }}>
            <div className="border-b border-gray-200 bg-gray-50/90 sticky top-0 z-10 flex items-center gap-2 px-3" style={{ height: `${ALTO_HEADER}px` }}>
              <span className="w-7 h-7 rounded-full bg-primary-100 text-primary-700 text-[11px] font-bold flex items-center justify-center flex-shrink-0">
                {iniciales(nombre)}
              </span>
              <span className="text-xs font-medium text-gray-800 truncate">{nombre}</span>
            </div>
            <div className="relative" style={{ height: `${largoTotal}px` }}>
              {marcas.map(m => (
                <div key={m.t} className={`absolute left-0 right-0 pointer-events-none ${m.enPunto ? 'border-t border-gray-100' : 'border-t border-dashed border-gray-50'}`} style={{ top: `${m.t * pxPorMinuto}px` }} />
              ))}
              {itemsCarril.map(a => {
                const anchoPct = 100 / numCarriles
                const estilo = ESTILO_ESTADO[a.estado] ?? ESTILO_DEFAULT
                return (
                  <button key={a.id} type="button" onClick={() => onSeleccionar(a)}
                    title={`${a.subproceso} — ${a.nombresEvaluado ?? ''} ${a.apellidosEvaluado ?? ''}`.trim()}
                    style={{
                      top: `${a._inicio * pxPorMinuto}px`,
                      height: `${a._duracionVisual * pxPorMinuto}px`,
                      left: `calc(${a._carril * anchoPct}% + 3px)`,
                      width: `calc(${anchoPct}% - 6px)`,
                    }}
                    className={`absolute rounded-md border-l-[3px] px-1.5 py-1 text-left overflow-hidden shadow-sm
                      ring-1 ring-black/5 hover:shadow-md hover:z-10 transition-all
                      ${estilo.borde_l} ${estilo.fondo} ${estilo.fondoHover}`}>
                    <span className={`flex items-center gap-1 text-[10px] font-semibold leading-tight truncate ${estilo.texto}`}>
                      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${estilo.punto}`} />
                      {a.subproceso}
                    </span>
                    <span className="block truncate text-[10px] text-gray-500 leading-tight pl-2.5">
                      {a.nombresEvaluado ?? ''} {a.apellidosEvaluado ?? ''}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        )
      })}

      {mostrarLineaActual && (
        <div className="absolute z-20 pointer-events-none" style={{ left: `${ANCHO_COL_HORA}px`, right: 0, top: `${ALTO_HEADER + minutoActual * pxPorMinuto}px` }}>
          <div className="absolute -top-[5px] -left-1.5 w-2.5 h-2.5 rounded-full bg-red-500 shadow-sm" />
          <div className="h-px w-full bg-red-500/70" />
        </div>
      )}
    </div>
  )
}

/* ─── Vista horizontal: horas en columnas, empleados en filas ─── */
function VistaHorizontal({ empleados, marcas, pxPorMinuto, onSeleccionar, mostrarLineaActual, minutoActual }) {
  const largoTotal = TOTAL_MIN * pxPorMinuto

  return (
    <div className="relative" style={{ minWidth: `${ANCHO_COL_EMPLEADO_H + largoTotal}px` }}>
      <div className="flex border-b border-gray-200 bg-gray-50/90 sticky top-0 z-10" style={{ height: `${ALTO_HEADER}px` }}>
        <div className="flex-shrink-0 sticky left-0 z-20 bg-gray-50/90 px-4 flex items-center text-xs font-semibold text-gray-600 tracking-wide uppercase" style={{ width: `${ANCHO_COL_EMPLEADO_H}px` }}>
          Empleado
        </div>
        <div className="relative" style={{ width: `${largoTotal}px` }}>
          {marcas.map(m => (
            <div key={m.t}
              className={`absolute top-0 bottom-0 flex items-center border-l ${m.enPunto ? 'border-gray-100 text-[11px] font-medium text-gray-400' : 'border-gray-50 text-[10px] text-gray-300'}`}
              style={{ left: `${m.t * pxPorMinuto}px`, paddingLeft: '4px' }}>
              {m.label}
            </div>
          ))}
        </div>
      </div>

      {empleados.map(([key, { nombre, items }]) => {
        const { items: itemsCarril, numCarriles } = empaquetarCarriles(items)
        const altoFila = numCarriles * ALTO_CARRIL
        return (
          <div key={key} className="flex border-b border-gray-100 last:border-0 hover:bg-gray-50/40 transition-colors">
            <div className="flex-shrink-0 sticky left-0 z-10 bg-white px-4 flex items-center gap-2.5 min-w-0" style={{ width: `${ANCHO_COL_EMPLEADO_H}px`, height: `${altoFila}px` }}>
              <span className="w-8 h-8 rounded-full bg-primary-100 text-primary-700 text-xs font-bold flex items-center justify-center flex-shrink-0">
                {iniciales(nombre)}
              </span>
              <span className="text-xs font-medium text-gray-800 truncate">{nombre}</span>
            </div>
            <div className="relative" style={{ width: `${largoTotal}px`, height: `${altoFila}px` }}>
              {marcas.map(m => (
                <div key={m.t} className={`absolute top-0 bottom-0 pointer-events-none ${m.enPunto ? 'border-l border-gray-100' : 'border-l border-dashed border-gray-50'}`} style={{ left: `${m.t * pxPorMinuto}px` }} />
              ))}
              {itemsCarril.map(a => {
                const estilo = ESTILO_ESTADO[a.estado] ?? ESTILO_DEFAULT
                return (
                  <button key={a.id} type="button" onClick={() => onSeleccionar(a)}
                    title={`${a.subproceso} — ${a.nombresEvaluado ?? ''} ${a.apellidosEvaluado ?? ''}`.trim()}
                    style={{
                      left: `${a._inicio * pxPorMinuto}px`,
                      width: `${a._duracionVisual * pxPorMinuto}px`,
                      top: `${a._carril * ALTO_CARRIL + 4}px`,
                      height: `${ALTO_CARRIL - 8}px`,
                    }}
                    className={`absolute rounded-md border-l-[3px] px-2 py-1.5 text-left overflow-hidden shadow-sm
                      ring-1 ring-black/5 hover:shadow-md hover:z-10 transition-all
                      ${estilo.borde_l} ${estilo.fondo} ${estilo.fondoHover}`}>
                    <span className={`flex items-center gap-1 text-[10px] font-semibold leading-tight truncate ${estilo.texto}`}>
                      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${estilo.punto}`} />
                      {a.subproceso}
                    </span>
                    <span className="block truncate text-[10px] text-gray-500 leading-tight mt-0.5 pl-2.5">
                      {a.nombresEvaluado ?? ''} {a.apellidosEvaluado ?? ''}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        )
      })}

      {mostrarLineaActual && (
        <div className="absolute top-0 bottom-0 z-20 pointer-events-none" style={{ left: `${ANCHO_COL_EMPLEADO_H + minutoActual * pxPorMinuto}px` }}>
          <div className="absolute -top-1.5 -left-[5px] w-2.5 h-2.5 rounded-full bg-red-500 shadow-sm" />
          <div className="w-px h-full bg-red-500/70" />
        </div>
      )}
    </div>
  )
}

/** @param dia "yyyy-MM-dd" del día que se está viendo — si es hoy, se dibuja la línea de hora actual. */
export default function CronogramaDia({ asignaciones, cargando, error, onSeleccionar, dia }) {
  const [orientacion, setOrientacion] = useState('vertical') // 'vertical' | 'horizontal'
  const [nivel, setNivel] = useState(NIVELES_ZOOM[0])

  if (cargando) {
    return (
      <div className="flex justify-center py-16">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
    )
  }

  const conHorario = (asignaciones ?? []).filter(a => a.fechaProgramada)

  if (conHorario.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm text-center py-16">
        <p className="text-sm text-gray-500 font-medium">Nadie tiene nada programado este día.</p>
        <p className="text-xs text-gray-400 mt-1">Los subprocesos programados aparecerán aquí en su horario.</p>
      </div>
    )
  }

  const porEmpleado = new Map()
  conHorario.forEach(a => {
    const key = a.idUsuarioAsignado ?? 'sin-asignar'
    if (!porEmpleado.has(key)) porEmpleado.set(key, { nombre: a.nombreAsignado ?? 'Sin asignar', items: [] })
    porEmpleado.get(key).items.push(a)
  })
  const empleados = [...porEmpleado.entries()]
  const marcas = generarMarcas(nivel.intervaloMin)

  const ahora = new Date()
  const esHoy = dia === hoyLocalDateString()
  const minutoActual = (ahora.getHours() * 60 + ahora.getMinutes()) - JORNADA_INICIO_H * 60
  const mostrarLineaActual = esHoy && minutoActual >= 0 && minutoActual <= TOTAL_MIN

  return (
    <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
      {/* ── Barra superior: orientación + jornada + zoom ── */}
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-gray-100 bg-gradient-to-r from-gray-50/80 to-white flex-wrap gap-2">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
            <button type="button" onClick={() => setOrientacion('vertical')}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${orientacion === 'vertical' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
              Vertical
            </button>
            <button type="button" onClick={() => setOrientacion('horizontal')}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${orientacion === 'horizontal' ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500 hover:text-gray-700'}`}>
              Horizontal
            </button>
          </div>
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <span>{String(JORNADA_INICIO_H).padStart(2, '0')}:00 – {String(JORNADA_FIN_H).padStart(2, '0')}:00</span>
            {mostrarLineaActual && (
              <span className="flex items-center gap-1 text-red-600 font-medium">
                <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                {ahora.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}
              </span>
            )}
          </div>
        </div>
        <ControlZoom nivel={nivel} onCambiar={setNivel} />
      </div>

      {/* ── Cronograma ── */}
      <div className="overflow-auto" style={{ maxHeight: `${ALTO_MAX_VISIBLE}px` }}>
        {orientacion === 'vertical' ? (
          <VistaVertical empleados={empleados} marcas={marcas} pxPorMinuto={nivel.pxPorMinuto}
            onSeleccionar={onSeleccionar} mostrarLineaActual={mostrarLineaActual} minutoActual={minutoActual} />
        ) : (
          <VistaHorizontal empleados={empleados} marcas={marcas} pxPorMinuto={nivel.pxPorMinuto}
            onSeleccionar={onSeleccionar} mostrarLineaActual={mostrarLineaActual} minutoActual={minutoActual} />
        )}
      </div>

      <Leyenda mostrarLineaActual={mostrarLineaActual} />
    </div>
  )
}
