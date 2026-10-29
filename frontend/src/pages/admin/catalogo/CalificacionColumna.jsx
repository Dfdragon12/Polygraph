import { etiquetaHallazgo } from '../../../services/hallazgosService'

// Constructor de plantillas: para qué sirve un campo en la calificación por hallazgos (V206).
// El catálogo (Admin → Calificación de riesgo → Catálogo de hallazgos) dice cuánto vale cada
// hallazgo; aquí solo se dice QUÉ campo los aplica y cuáles.

export const MODOS = [
  { valor: '', etiqueta: 'No califica' },
  { valor: 'HALLAZGO', etiqueta: 'Es un hallazgo — quien diligencia lo elige del catálogo' },
  { valor: 'OPCIONES', etiqueta: 'Genera un hallazgo según la opción elegida' },
  { valor: 'CRITICIDAD', etiqueta: 'Criticidad del cargo (Estándar / Sensible / Crítico)' },
  { valor: 'CONDICION', etiqueta: 'Condición: "Sí" habilita los hallazgos solo con condición (conducción)' },
]

export const RESULTADOS_CAMPO = [
  { valor: 'PUNTAJE', etiqueta: 'Puntaje (60,0)' },
  { valor: 'FORMULA', etiqueta: 'Fórmula (30 + 15 = 45 de 100)' },
  { valor: 'NIVEL', etiqueta: 'Nivel (BAJO / MEDIO / ALTO)' },
  { valor: 'SEMAFORO_BAJO', etiqueta: 'Semáforo — casilla BAJO' },
  { valor: 'SEMAFORO_MEDIO', etiqueta: 'Semáforo — casilla MEDIO' },
  { valor: 'SEMAFORO_ALTO', etiqueta: 'Semáforo — casilla ALTO' },
  { valor: 'NOTA', etiqueta: 'Nota (texto si un campo o tabla no tiene nada)' },
  { valor: 'MINIMO_FILAS', etiqueta: 'Filas de una tabla (genera un hallazgo si hay menos de N)' },
  { valor: 'PROMEDIO_TABLA', etiqueta: 'Promedio de una tabla (el de su columna «promedio de la fila»)' },
]
export const RESULTADOS_HALLAZGOS = [
  { valor: 'REF', etiqueta: 'Código (Ref.)', nombre: 'Ref.', clave: 'h_ref' },
  { valor: 'DESCRIPCION', etiqueta: 'Hallazgo', nombre: 'Hallazgo', clave: 'h_descripcion' },
  { valor: 'FUENTE', etiqueta: 'Fuente', nombre: 'Fuente', clave: 'h_fuente' },
  { valor: 'DETALLE', etiqueta: 'Detalle', nombre: 'Detalle', clave: 'h_detalle' },
  { valor: 'PUNTOS', etiqueta: 'Puntos', nombre: 'Puntos', clave: 'h_puntos' },
]
const RESULTADOS_TABLA = [
  { valor: 'NUMERO_FILA', etiqueta: 'Número de fila (1, 2, 3…)' },
  { valor: 'PROMEDIO_FILA', etiqueta: 'Promedio de varios campos de la fila (genera un hallazgo si queda por debajo de N)' },
  { valor: 'CODIGOS_FILA', etiqueta: 'Códigos de hallazgo marcados en la fila' },
  { valor: 'TEXTO_SI', etiqueta: 'Texto si otro campo de la fila tiene un valor' },
]

export const OPCIONES_CRITICIDAD = ['Estándar', 'Sensible', 'Crítico']

// Cálculos que guardan referencia (y regla «CÓDIGO<N») y, si hay regla, fuente y detalle del hallazgo que generan.
const CON_REFERENCIA = ['NOTA', 'TEXTO_SI', 'PROMEDIO_FILA', 'MINIMO_FILAS', 'PROMEDIO_TABLA']
const GENERAN_HALLAZGO = ['PROMEDIO_FILA', 'MINIMO_FILAS']
const REGLA_MENOR = /^\s*[A-Za-z0-9-]+\s*<\s*\d+([.,]\d+)?\s*$/

const cls = 'border border-gray-200 rounded-lg px-2 py-1 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'

/** Estado local inicial a partir de lo que manda el servidor (ColumnaResponse.calificacion). */
export function calificacionDesdeServidor(cal) {
  const porOpcion = Object.fromEntries((cal?.porOpcion ?? []).map(h => [h.opcion, h.codigo]))
  return {
    modo: cal?.uso ?? (Object.keys(porOpcion).length ? 'OPCIONES' : ''),
    calculo: cal?.calculo ?? '',
    referencia: cal?.referencia ?? '',
    textoNota: cal?.textoNota ?? '',
    fuente: cal?.fuente ?? '',
    detalle: cal?.detalle ?? '',
    porOpcion,
  }
}

export const calificacionVacia = () => calificacionDesdeServidor(null)

/** Lo que va al servidor (GuardarPlantillaRequest.ColumnaRequest.calificacion). */
export function calificacionParaServidor(c, opciones) {
  const cal = c.cal ?? calificacionVacia()
  const generaPorRegla = c.calculado && GENERAN_HALLAZGO.includes(cal.calculo) && !!cal.textoNota?.trim()
  const da = cal.modo === 'HALLAZGO' || cal.modo === 'OPCIONES' || generaPorRegla
  return {
    uso: ['HALLAZGO', 'CRITICIDAD', 'CONDICION'].includes(cal.modo) && !c.calculado ? cal.modo : null,
    calculo: c.calculado ? (cal.calculo || null) : null,
    referencia: c.calculado && CON_REFERENCIA.includes(cal.calculo) ? (cal.referencia?.trim() || null)
      : c.calculado && cal.calculo === 'FORMULA' && cal.referencia === 'CON_CODIGO' ? 'CON_CODIGO' : null,
    textoNota: c.calculado && CON_REFERENCIA.includes(cal.calculo) ? (cal.textoNota?.trim() || null) : null,
    fuente: da ? (cal.fuente?.trim() || null) : null,
    detalle: da ? (cal.detalle?.trim() || null) : null,
    porOpcion: cal.modo === 'OPCIONES' && !c.calculado
      ? opciones.filter(o => cal.porOpcion[o]).map(o => ({ opcion: o, codigo: cal.porOpcion[o] }))
      : [],
  }
}

/** Mensaje de error si la configuración está incompleta (null si está bien). */
export function validarCalificacion(c, opciones) {
  const cal = c.cal ?? calificacionVacia()
  const nombre = c.nombre || '(sin título)'
  if (c.calculado) {
    if (cal.calculo === 'NOTA' && (!cal.referencia || !cal.textoNota?.trim())) return `"${nombre}": la nota necesita el campo o tabla que mira y su texto.`
    if (cal.calculo === 'TEXTO_SI' && (!cal.referencia?.includes('=') || !cal.textoNota?.trim())) return `"${nombre}": el texto condicional necesita "campo=Valor" y el texto que sale.`
    if (cal.calculo === 'PROMEDIO_FILA' && !cal.referencia?.trim()) return `"${nombre}": el promedio de la fila necesita los campos que promedia (separados por coma).`
    if (cal.calculo === 'PROMEDIO_FILA' && cal.textoNota?.trim() && !REGLA_MENOR.test(cal.textoNota)) return `"${nombre}": la regla del promedio se escribe «CÓDIGO<número» (ej. PER-03<3).`
    if (cal.calculo === 'MINIMO_FILAS' && (!cal.referencia?.trim() || !REGLA_MENOR.test(cal.textoNota ?? ''))) return `"${nombre}": las filas mínimas necesitan la tabla que cuenta y la regla «CÓDIGO<número» (ej. PER-05<2).`
    if (cal.calculo === 'PROMEDIO_TABLA' && !cal.referencia?.trim()) return `"${nombre}": el promedio de la tabla necesita la tabla que promedia.`
    return null
  }
  if (cal.modo === 'HALLAZGO' && !opciones.some(o => o !== '—')) return `"${nombre}": elige al menos un hallazgo que se pueda escoger.`
  if (cal.modo === 'OPCIONES' && !opciones.some(o => cal.porOpcion[o])) return `"${nombre}": elige qué hallazgo genera al menos una opción.`
  return null
}

/**
 * Bloque "Calificación" de una columna. onCambio(cambios) aplica cambios a la columna (cal,
 * tipoDato, opcionesTexto). catalogo: hallazgos del área de esta plantilla. claves: campos y
 * tablas de la plantilla (para la nota).
 */
export default function CalificacionColumna({ c, seccion, catalogo, claves, opciones, onCambio }) {
  const cal = c.cal ?? calificacionVacia()
  const setCal = (k, v) => onCambio({ cal: { ...cal, [k]: v } })

  if (c.calculado) {
    const lista = seccion.calculada ? RESULTADOS_HALLAZGOS : seccion.tipo === 'TABLA' ? RESULTADOS_TABLA : RESULTADOS_CAMPO
    return (
      <div className="rounded-lg border border-violet-100 bg-violet-50/40 px-3 py-2 space-y-1.5">
        <label className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
          <span className="font-medium text-violet-800">Resultado que muestra</span>
          <select value={cal.calculo} onChange={e => setCal('calculo', e.target.value)} className={cls}>
            <option value="">— Elige —</option>
            {lista.map(r => <option key={r.valor} value={r.valor}>{r.etiqueta}</option>)}
          </select>
        </label>
        {cal.calculo === 'FORMULA' && (
          <label className="flex items-center gap-2 text-xs text-gray-600">
            <input type="checkbox" checked={cal.referencia === 'CON_CODIGO'} onChange={e => setCal('referencia', e.target.checked ? 'CON_CODIGO' : '')} />
            Con un solo hallazgo, mostrar el código (ej. «LAB-07: 50 de 100 posibles»)
          </label>
        )}
        {cal.calculo === 'TEXTO_SI' && (
          <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
            <span>Sale si</span>
            <input aria-label="Campo de la fila y valor, con el formato campo=Valor" value={cal.referencia} onChange={e => setCal('referencia', e.target.value)} maxLength={150}
              placeholder="campo=Valor (ej. v_validado_por=Pendiente)" className={`${cls} min-w-[16rem]`} />
            <span>y entonces dice:</span>
            <input aria-label="Texto que sale" value={cal.textoNota} onChange={e => setCal('textoNota', e.target.value)} maxLength={500}
              className={`${cls} flex-1 min-w-[16rem]`} />
          </div>
        )}
        {cal.calculo === 'PROMEDIO_FILA' && (
          <div className="space-y-1.5 text-xs text-gray-600">
            <label className="flex flex-wrap items-center gap-2">
              <span>Promedia los campos</span>
              <input aria-label="Claves de los campos que promedia, separadas por coma" value={cal.referencia} onChange={e => setCal('referencia', e.target.value)} maxLength={300}
                placeholder="r_a1,r_a2,r_a3" className={`${cls} flex-1 min-w-[16rem]`} />
            </label>
            <ReglaHallazgo cal={cal} setCal={setCal} placeholder="PER-03<3" ayuda="Si el promedio queda por debajo, el sistema suma ese hallazgo solo (déjalo vacío para no generar ninguno)." />
          </div>
        )}
        {cal.calculo === 'MINIMO_FILAS' && (
          <div className="space-y-1.5 text-xs text-gray-600">
            <label className="flex flex-wrap items-center gap-2">
              <span>Cuenta las filas de</span>
              <select aria-label="Tabla cuyas filas cuenta" value={cal.referencia} onChange={e => setCal('referencia', e.target.value)} className={cls}>
                <option value="">— tabla —</option>
                {claves.map(k => <option key={k.clave} value={k.clave}>{k.etiqueta}</option>)}
              </select>
            </label>
            <ReglaHallazgo cal={cal} setCal={setCal} placeholder="PER-05<2" ayuda="Si hay menos filas, el sistema suma ese hallazgo una vez. En el detalle, {#} es cuántas hay." />
          </div>
        )}
        {cal.calculo === 'PROMEDIO_TABLA' && (
          <label className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
            <span>Promedia la columna «promedio de la fila» de</span>
            <select aria-label="Tabla que promedia" value={cal.referencia} onChange={e => setCal('referencia', e.target.value)} className={cls}>
              <option value="">— tabla —</option>
              {claves.map(k => <option key={k.clave} value={k.clave}>{k.etiqueta}</option>)}
            </select>
          </label>
        )}
        {cal.calculo === 'NOTA' && (
          <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
            <span>Sale si</span>
            <select aria-label="Campo o tabla que decide si sale la nota" value={cal.referencia} onChange={e => setCal('referencia', e.target.value)} className={cls}>
              <option value="">— campo o tabla —</option>
              {claves.map(k => <option key={k.clave} value={k.clave}>{k.etiqueta}</option>)}
            </select>
            <span>no tiene nada (vacío, "No", "—" o tabla sin filas):</span>
            <input aria-label="Texto de la nota" value={cal.textoNota} onChange={e => setCal('textoNota', e.target.value)} maxLength={500}
              placeholder="Ej: No se encontraron procesos judiciales." className={`${cls} flex-1 min-w-[16rem]`} />
          </div>
        )}
      </div>
    )
  }

  const elegirModo = (modo) => {
    const cambios = { cal: { ...cal, modo } }
    if (modo === 'HALLAZGO') {
      cambios.tipoDato = 'LISTA'
      cambios.opcionesTexto = ['—', ...opciones.filter(o => catalogo.some(h => h.codigo === o))].join('\n')
      cambios.valorDefecto = '—'
    } else if (modo === 'CRITICIDAD') {
      cambios.tipoDato = 'LISTA'
      cambios.opcionesTexto = OPCIONES_CRITICIDAD.join('\n')
    } else if (modo === 'CONDICION') {
      cambios.tipoDato = 'LISTA'
      if (!opciones.includes('Sí')) cambios.opcionesTexto = 'Sí\nNo'
    } else if (modo === 'OPCIONES' && c.tipoDato !== 'LISTA') {
      cambios.tipoDato = 'LISTA'
    }
    onCambio(cambios)
  }

  const elegidos = new Set(opciones)
  const alternar = (codigo) => {
    const nuevos = catalogo.map(h => h.codigo).filter(k => (k === codigo ? !elegidos.has(k) : elegidos.has(k)))
    onCambio({ opcionesTexto: ['—', ...nuevos].join('\n'), valorDefecto: '—' })
  }

  return (
    <div className="rounded-lg border border-primary-100 bg-primary-50/30 px-3 py-2 space-y-2">
      <label className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
        <span className="font-medium text-primary-800">Calificación</span>
        <select value={cal.modo} onChange={e => elegirModo(e.target.value)} className={`${cls} max-w-full`}>
          {MODOS.filter(m => m.valor !== 'OPCIONES' || c.tipoDato === 'LISTA' || cal.modo === 'OPCIONES')
            .filter(m => !['CRITICIDAD', 'CONDICION'].includes(m.valor) || seccion.tipo !== 'TABLA')
            .map(m => <option key={m.valor} value={m.valor}>{m.etiqueta}</option>)}
        </select>
      </label>

      {cal.modo === 'HALLAZGO' && (
        <div>
          <p className="text-[11px] text-gray-500 mb-1">Hallazgos que se pueden escoger en este campo ({elegidos.size ? [...elegidos].filter(o => o !== '—').length : 0}):</p>
          {catalogo.length === 0 ? (
            <p className="text-[11px] text-amber-700">No hay hallazgos para esta área. Créalos en Calificación de riesgo → Catálogo de hallazgos.</p>
          ) : (
            <div className="max-h-48 overflow-y-auto rounded-md border border-gray-100 bg-white divide-y divide-gray-50">
              {catalogo.map(h => (
                <label key={h.codigo} className="flex items-start gap-2 px-2 py-1 text-xs text-gray-700 cursor-pointer hover:bg-gray-50">
                  <input type="checkbox" checked={elegidos.has(h.codigo)} onChange={() => alternar(h.codigo)}
                    className="mt-0.5 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                  <span>{etiquetaHallazgo(h)}</span>
                </label>
              ))}
            </div>
          )}
        </div>
      )}

      {cal.modo === 'OPCIONES' && (
        opciones.length === 0 ? (
          <p className="text-[11px] text-amber-700">Escribe las opciones de la lista para asignarles hallazgos.</p>
        ) : (
          <div className="rounded-md border border-gray-100 bg-white divide-y divide-gray-50">
            {opciones.map(o => (
              <div key={o} className="grid grid-cols-[minmax(0,10rem)_minmax(0,1fr)] gap-2 items-center px-2 py-1">
                <span className="text-xs text-gray-700 truncate" title={o}>{o}</span>
                <select aria-label={`Hallazgo para la opción ${o}`} value={cal.porOpcion[o] ?? ''} onChange={e => setCal('porOpcion', { ...cal.porOpcion, [o]: e.target.value })} className={cls}>
                  <option value="">No genera hallazgo</option>
                  {catalogo.map(h => <option key={h.codigo} value={h.codigo}>{etiquetaHallazgo(h)}</option>)}
                </select>
              </div>
            ))}
          </div>
        )
      )}

      {(cal.modo === 'HALLAZGO' || cal.modo === 'OPCIONES') && (
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-[11px] text-gray-500">
            Fuente en la tabla de hallazgos
            <input value={cal.fuente} onChange={e => setCal('fuente', e.target.value)} maxLength={300}
              placeholder={`Vacío = "${seccion.nombre || 'nombre de la sección'}"`} className={`${cls} mt-0.5 w-full`} />
          </label>
          <label className="text-[11px] text-gray-500">
            Detalle en la tabla de hallazgos
            <input value={cal.detalle} onChange={e => setCal('detalle', e.target.value)} maxLength={300}
              placeholder={seccion.tipo === 'TABLA' ? 'Vacío = lo diligenciado en la fila' : 'Vacío = "Etiqueta: valor"'}
              className={`${cls} mt-0.5 w-full`} />
          </label>
          <p className="sm:col-span-2 text-[11px] text-gray-400">
            Usa {'{clave}'} para poner el valor de otro campo, ej. <code>{'Proceso N° {p_numero}: {p_delito}'}</code>.
          </p>
        </div>
      )}
    </div>
  )
}

/** Regla «CÓDIGO<N» de un cálculo que genera un hallazgo por sí solo, y cómo sale en la tabla de hallazgos. */
function ReglaHallazgo({ cal, setCal, placeholder, ayuda }) {
  return (
    <div className="space-y-1.5">
      <label className="flex flex-wrap items-center gap-2">
        <span>Genera el hallazgo si es menor a</span>
        <input aria-label="Regla con el formato CÓDIGO<número" value={cal.textoNota} onChange={e => setCal('textoNota', e.target.value)} maxLength={60}
          placeholder={placeholder} className={`${cls} w-40`} />
        <span className="text-gray-500">{ayuda}</span>
      </label>
      {!!cal.textoNota?.trim() && (
        <div className="flex flex-wrap items-center gap-2">
          <input aria-label="Fuente que sale en la tabla de hallazgos" value={cal.fuente} onChange={e => setCal('fuente', e.target.value)} maxLength={300}
            placeholder="Fuente (ej. Referencia N° {r_numero})" className={`${cls} flex-1 min-w-[14rem]`} />
          <input aria-label="Detalle que sale en la tabla de hallazgos" value={cal.detalle} onChange={e => setCal('detalle', e.target.value)} maxLength={500}
            placeholder="Detalle (ej. {r_nombre} ({r_parentesco}))" className={`${cls} flex-1 min-w-[14rem]`} />
        </div>
      )}
    </div>
  )
}
