import { useState } from 'react'
import CampoFecha from '../../components/CampoFecha'
import agendaService from '../../services/agendaService'
import { InputPorTipo } from '../shared/Formulario'
import AreaTexto, { NoImprime } from './AreaTexto'
import GestionesTabla from './GestionesTabla'
import { Marca, Declaro, Fila, Opciones, valoresPorId } from './VinculacionesLaboral'
import { accesoEstudio, listasDeEstudio } from './TemasAcademica'
import { leerInstantanea, cambioDeclaracion } from './reglasAcademica'
import {
  CATEGORIAS, DOCUMENTOS_BOTON, PREGUNTAS, SIN_CODIGO, valoresDeclarados, marcasDelEstudio, sugerenciaDeFondo, codigoDePreguntas,
  AYUDA_EXTERIOR, CRITERIO_NO_VERIFICABLE, esDelExterior, sugerirCriterioNoVerificable, leerPreguntas, escribirPreguntas, avisoDocumento, borradorObservaciones, filaInformativa,
} from './reglasEstudio'
import { fechaLimite, fechaAIso, isoAFecha, isoATexto, fechaATexto, diasEntre } from './reglasLaboral'

// AC-03 · Fase 2 de la verificación académica: la tabla «5. Estudio verificado» llega con lo declarado y cada celda lleva lo confirmado y, debajo, «Declaró: …» con la marca
// coincide / revisar. Debajo van las preguntas que llevan al código, los campos que solo aplican con ciertos códigos, las gestiones con el plazo y el soporte.
// Se guardan las columnas de siempre más aca_preguntas y aca_soporte (V254); el Word no cambia.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const inputCls = 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
const SIN_ARCHIVOS = (v) => !tiene(v) || v === '[]'
const CON_ALTERACION = 'Alteración evidente en inspección visual'
const SIN_SOPORTE_OBLIGATORIO = ['ACA-04', 'ALR-ACA-02']

export default function EstudioAcademica({ secciones, estudios, soloLectura, idServicioSubproceso, onCampo, onCelda, onAgregar, onQuitar, onError }) {
  const a = accesoEstudio(secciones, onCampo)
  const sg = secciones.find(s => s.clave === 'gestiones')
  const si = secciones.find(s => s.clave === 'informativos')
  const sec = secciones.find(s => s.clave === 'estudio_verificado' && !s.origenFijoClave)
  const [noHabiles, setNoHabiles] = useState(new Set())
  if (!sec) return <p className="text-gray-700">Este formato no tiene la sección del estudio verificado.</p>

  const v = a.valores()
  const colDe = (clave) => sec.columnas.find(c => c.clave === clave)
  const opcionesDe = (clave) => (colDe(clave)?.opciones ?? []).filter(o => o && o !== SIN_CODIGO)
  const copia = leerInstantanea(v.aca_decl)
  const declarado = valoresDeclarados(copia, listasDeEstudio(a))
  const marcas = marcasDelEstudio(v, copia, listasDeEstudio(a))
  const cambio = cambioDeclaracion(v.aca_estudio_sel, copia, estudios)
  const poner = (clave, valor) => a.poner(clave, valor)
  const hoy = new Date()
  const hoyIso = fechaAIso(hoy)

  const resp = leerPreguntas(v.aca_preguntas)
  const derivado = codigoDePreguntas(resp)
  const sugeridoFondo = sugerenciaDeFondo(marcas)
  const ref = tiene(v.aca_ref) ? v.aca_ref : SIN_CODIGO
  const distintoAlSugerido = ref !== derivado.codigo

  const responder = (id, valor) => {
    const nuevo = { ...resp }
    if (valor) nuevo[id] = valor; else delete nuevo[id]
    poner('aca_preguntas', escribirPreguntas(nuevo))
    poner('aca_ref', codigoDePreguntas(nuevo).codigo)
  }

  const limite = tiene(v.aca_fecha_limite) ? isoAFecha(v.aca_fecha_limite) : null
  const quedan = limite ? diasEntre(new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate()), limite) : null

  const solicitarDocumento = async () => {
    let festivos = noHabiles
    try {
      const fin = new Date(hoy); fin.setDate(fin.getDate() + 30)
      festivos = new Set((await agendaService.diasNoHabiles(hoyIso, fechaAIso(fin))).map(d => d.fecha))
      setNoHabiles(festivos)
    } catch { onError?.('No se pudieron leer los festivos: la fecha límite cuenta solo sábados y domingos como no hábiles.') }
    poner('aca_fecha_sol_eval', hoyIso)
    poner('aca_fecha_limite', fechaAIso(fechaLimite(hoy, festivos)))
  }

  const registrarGestion = () => sg && onAgregar(sg.idSeccion, valoresPorId(sg, { g_fecha: hoyIso }))
  const yaEnInformativos = si && si.filas.some(f => String(f.valores[si.columnas.find(c => c.clave === 'i_situacion')?.idColumna] ?? '').startsWith('Diferencia menor frente a lo declarado'))
  const agregarInformativo = () => si && onAgregar(si.idSeccion, valoresPorId(si, filaInformativa(v, marcas)))

  const texto = (clave, etiqueta, extra) => (
    <Fila etiqueta={etiqueta} clave={clave}>
      <input aria-label={etiqueta} className={inputCls} value={v[clave]} disabled={soloLectura} onChange={e => poner(clave, e.target.value)} />
      {extra}
    </Fila>
  )
  const declaro = (clave, extra) => (declarado && tiene(declarado[clave]) ? <Declaro valor={declarado[clave]} marca={<Marca r={marcas[clave]} />} extra={extra} /> : null)
  const declaroFecha = (clave) => {
    if (!declarado || !tiene(declarado[clave])) return null
    const m = marcas[clave]
    return <Declaro valor={isoATexto(declarado[clave])} marca={<Marca r={m} texto={m && m.dias > 0 ? `Diferencia: ${m.dias} ${m.dias === 1 ? 'día' : 'días'} · ${m.nivel === 'fondo' ? 'de fondo' : 'informativo'}` : null} />} />
  }
  const fecha = (clave, etiqueta) => (
    <Fila etiqueta={etiqueta} clave={clave}>
      <CampoFecha aria-label={etiqueta} value={v[clave]} disabled={soloLectura} onChange={iso => poner(clave, iso)} />
      {declaroFecha(clave)}
    </Fila>
  )

  const avisoDoc = avisoDocumento(v.aca_estado, v.aca_documento)
  const otrosDocumentos = opcionesDe('aca_documento').filter(o => !DOCUMENTOS_BOTON.includes(o))
  const exterior = esDelExterior(v)
  const sugiereNoVerificable = sugerirCriterioNoVerificable({ v, plazoVencido: quedan !== null && quedan < 0, criterio: v.aca_criterio })
  const exigeFechaSol = ref === 'ACA-04' || tiene(v.aca_fecha_sol_eval)

  return (
    <div className="space-y-5">
      {!tiene(v.aca_estudio_sel) && (
        <p className="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2" role="status">
          {estudios.length > 0 ? 'Aún no hay un estudio seleccionado: elígelo en «Educación declarada y criterio». Mientras tanto puedes escribir lo que confirme la institución, sin comparación.' : 'No hay estudio declarado para verificar.'}
        </p>
      )}
      {sugiereNoVerificable && <p role="status" className="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">El estudio es del exterior y el plazo del evaluado venció: se sugiere el criterio «{CRITERIO_NO_VERIFICABLE}» (se cambia en «Educación declarada y criterio»). Tú decides.</p>}
      {cambio === 'cambio' && <p role="alert" className="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">El evaluado cambió su declaración de este estudio. Lo declarado que ves abajo es lo que había al seleccionarlo.</p>}
      {cambio === 'desaparecio' && <p role="alert" className="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">Este estudio ya no aparece en la hoja de vida del evaluado.</p>}

      <section aria-label="Estudio verificado" className="border border-gray-300 rounded">
        <h3 className="text-white text-sm font-bold px-3 py-2 rounded-t uppercase tracking-wide" style={{ background: '#7b1f2b' }}>5. Estudio verificado</h3>

        <Fila etiqueta="Criterio de verificación" clave="aca_criterio_lectura">
          <p className="text-sm text-gray-900">{v.aca_criterio || '—'}</p>
          <p className="text-xs text-gray-600">Se elige en «Educación declarada y criterio».</p>
        </Fila>
        {texto('aca_institucion', 'Institución', declaro('aca_institucion'))}
        {texto('aca_ciudad', exterior ? 'País y ciudad' : 'Ciudad (y país si es del exterior)', <>{exterior && <p className="text-xs text-amber-900">El estudio es del exterior: escribe también el país.</p>}{declaro('aca_ciudad')}</>)}
        <Fila etiqueta="Categoría" clave="aca_categoria">
          <Opciones etiqueta="Categoría" opciones={CATEGORIAS.filter(c => (colDe('aca_categoria')?.opciones ?? []).includes(c))} valor={v.aca_categoria} deshabilitado={soloLectura} onElegir={x => poner('aca_categoria', x)} />
          {declaro('aca_categoria')}
        </Fila>
        <Fila etiqueta="Nivel específico" clave="aca_nivel">
          <Opciones etiqueta="Nivel específico" opciones={opcionesDe('aca_nivel')} valor={v.aca_nivel} deshabilitado={soloLectura} onElegir={x => poner('aca_nivel', x)} />
          {declaro('aca_nivel')}
        </Fila>
        {fecha('aca_fecha_inicio', 'Fecha de inicio')}
        {fecha('aca_fecha_fin', 'Fecha de finalización')}
        {texto('aca_titulo', 'Título obtenido', declaro('aca_titulo'))}
        <Fila etiqueta="Estado del estudio" clave="aca_estado">
          <Opciones etiqueta="Estado del estudio" opciones={opcionesDe('aca_estado')} valor={v.aca_estado} deshabilitado={soloLectura} onElegir={x => poner('aca_estado', x)} />
          {declaro('aca_estado')}
        </Fila>
        <Fila etiqueta="Documento aportado" clave="aca_documento">
          <div className="flex flex-wrap gap-2 items-start">
            <Opciones etiqueta="Documento aportado" opciones={DOCUMENTOS_BOTON} valor={v.aca_documento} deshabilitado={soloLectura} onElegir={x => poner('aca_documento', x)} />
            <select aria-label="Otro documento aportado" className="min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100 max-w-full" disabled={soloLectura}
              value={otrosDocumentos.includes(v.aca_documento) ? v.aca_documento : ''} onChange={e => poner('aca_documento', e.target.value)}>
              <option value="">Otro…</option>
              {otrosDocumentos.map(o => <option key={o} value={o}>{o}</option>)}
            </select>
          </div>
          {avisoDoc && <p role="alert" className="mt-2 text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded p-2">{avisoDoc}</p>}
        </Fila>
        <Fila etiqueta="Medio de verificación" clave="aca_medio">
          <select aria-label="Medio de verificación" className={inputCls} value={v.aca_medio} disabled={soloLectura} onChange={e => poner('aca_medio', e.target.value)}>
            <option value="">Selecciona…</option>
            {opcionesDe('aca_medio').map(o => <option key={o} value={o}>{o}</option>)}
          </select>
        </Fila>
        <Fila etiqueta="Contacto en la institución" clave="aca_contacto">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <input aria-label="Nombre del contacto" placeholder="Nombre" className={inputCls} value={v.aca_contacto} disabled={soloLectura} onChange={e => poner('aca_contacto', e.target.value)} />
            <input aria-label="Cargo del contacto" placeholder="Cargo" className={inputCls} value={v.aca_contacto_cargo} disabled={soloLectura} onChange={e => poner('aca_contacto_cargo', e.target.value)} />
          </div>
        </Fila>
        {texto('aca_contacto_tel', 'Teléfono / correo')}
        {fecha('aca_fecha_respuesta', 'Fecha de respuesta')}
        <Fila etiqueta="Calificación" clave="aca_ref">
          <select aria-label="Calificación" className={inputCls} value={ref} disabled={soloLectura} onChange={e => poner('aca_ref', e.target.value)}>
            {(colDe('aca_ref')?.opciones ?? []).filter(Boolean).map(o => <option key={o} value={o}>{o}</option>)}
          </select>
          <p className="text-xs text-gray-700 mt-1">
            Sugerido: <b>{derivado.codigo}</b>{derivado.razon ? ` · ${derivado.razon}` : ' · ninguna pregunta aplica todavía'}. Tú confirmas.
            {distintoAlSugerido && <b className="text-amber-900"> Elegiste otro código: explica el motivo en Observaciones.</b>}
          </p>
        </Fila>
        <Fila etiqueta="Observaciones" clave="aca_observaciones">
          <AreaTexto ariaLabel="Observaciones" rows={3} max={colDe('aca_observaciones')?.longitudMaxima} value={v.aca_observaciones} disabled={soloLectura} onChange={x => poner('aca_observaciones', x)} />
          {!soloLectura && !tiene(v.aca_observaciones) && tiene(v.aca_titulo) && (
            <button type="button" className="mt-1 min-h-11 text-sm underline text-primary-700" onClick={() => poner('aca_observaciones', borradorObservaciones(v))}>Sugerir un borrador con lo confirmado</button>
          )}
        </Fila>
      </section>

      <section aria-labelledby="ac-preg" className="border border-gray-200 rounded p-3 bg-gray-50">
        <div className="flex items-center justify-between gap-2 mb-2">
          <h3 id="ac-preg" className="text-sm font-bold text-gray-900">Preguntas para el código</h3>
          <NoImprime />
        </div>
        <p className="text-xs text-gray-700 mb-2">El código es el de la primera pregunta que aplica. El sistema sugiere; tú decides.</p>
        <ul className="space-y-3" data-clave="aca_preguntas">
          {PREGUNTAS.map((q, n) => (
            <li key={q.id}>
              <p className="text-sm text-gray-900">{n + 1}. {q.texto}
                {q.id === 'p3' && sugeridoFondo && resp.p3 !== sugeridoFondo.valor && <span className="ml-2 text-xs rounded bg-amber-100 text-amber-900 px-1.5 py-0.5">Sugerido: {sugeridoFondo.valor} · {sugeridoFondo.porque}</span>}
              </p>
              {q.id === 'p4' && exterior && <p className="text-xs text-amber-900 mb-1">{AYUDA_EXTERIOR}</p>}
              <Opciones etiqueta={q.texto} opciones={q.opciones} valor={resp[q.id] ?? ''} deshabilitado={soloLectura} onElegir={x => responder(q.id, x)} />
              {q.id === 'p3' && resp.p3 === 'Diferencia menor explicada' && !soloLectura && (
                <p className="text-xs text-gray-700 mt-1">
                  No suma puntos (calificación «—»). {yaEnInformativos ? 'Ya está en «Hallazgos sin impacto».' : <button type="button" className="underline text-primary-700 min-h-11" onClick={agregarInformativo}>Agregar la fila a «Hallazgos sin impacto»</button>}
                </p>
              )}
            </li>
          ))}
        </ul>

        {ref === 'ACA-01' && (
          <div className="mt-3 space-y-2" data-clave="aca_ac01_base">
            <p className="text-sm font-semibold text-gray-900 flex flex-wrap items-center gap-2">Sustento del ACA-01 <NoImprime /></p>
            <Opciones etiqueta="Sustento del ACA-01" opciones={opcionesDe('aca_ac01_base')} valor={v.aca_ac01_base} deshabilitado={soloLectura} onElegir={x => poner('aca_ac01_base', x)} />
            {v.aca_ac01_base === CON_ALTERACION && (
              <div data-clave="aca_vobo" className="text-sm bg-white border border-gray-200 rounded p-2">
                <p className="font-semibold text-gray-900">Visto bueno del segundo analista</p>
                <p className="text-gray-800">{tiene(v.aca_vobo) && v.aca_vobo !== SIN_CODIGO ? v.aca_vobo : 'Pendiente. Lo da otro analista desde su sesión; no se escribe a mano. Adjunta también la evidencia en el soporte de abajo.'}</p>
              </div>
            )}
          </div>
        )}
      </section>

      <section aria-labelledby="ac-gest" data-clave="ac-gestiones" className="border border-gray-200 rounded p-3">
        <div className="flex items-center justify-between gap-2 mb-2">
          <h3 id="ac-gest" className="text-sm font-bold text-gray-900">Gestiones y plazo</h3>
          <NoImprime />
        </div>
        {(sg?.filas.length ?? 0) === 0 && <p className="text-sm text-gray-700 mb-2">Sin gestiones registradas.</p>}
        <GestionesTabla seccion={sg} filas={sg?.filas ?? []} soloLectura={soloLectura} onCelda={onCelda} onQuitar={onQuitar} />
        {!soloLectura && sg && <button type="button" onClick={registrarGestion} className="mt-2 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Registrar gestión</button>}

        <div className="mt-3" data-clave="aca_fecha_sol_eval">
          {!soloLectura && <button type="button" onClick={solicitarDocumento} className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">Solicitar documento al evaluado</button>}
          {exigeFechaSol && !tiene(v.aca_fecha_sol_eval) && <p className="mt-2 text-sm text-red-800">Con ACA-04 hace falta la fecha de solicitud: usa «Solicitar documento al evaluado».</p>}
          {limite && (
            <p className={`mt-2 text-sm ${quedan < 0 ? 'text-red-800' : 'text-gray-800'}`}>
              Solicitado el {isoATexto(v.aca_fecha_sol_eval)} · fecha límite: <b>{fechaATexto(limite)}</b> (4 días hábiles) ·{' '}
              {quedan > 0 ? `quedan ${quedan} ${quedan === 1 ? 'día' : 'días'}` : quedan === 0 ? 'vence hoy' : `venció hace ${-quedan} ${-quedan === 1 ? 'día' : 'días'}`}
            </p>
          )}
          {limite && quedan < 0 && ref !== 'ACA-04' && <p role="alert" className="text-sm text-red-800 mt-1">Venció el plazo del evaluado: cierra como ACA-04 si no aportó lo solicitado.</p>}
        </div>

        <div className="mt-3" data-clave="aca_soporte">
          <p className="text-sm font-semibold text-gray-900 mb-1 flex flex-wrap items-center gap-2">Soporte de la verificación <NoImprime /></p>
          <p className="text-xs text-gray-700 mb-1">
            {SIN_SOPORTE_OBLIGATORIO.includes(ref) ? 'Con este código el soporte son las gestiones: adjuntar es opcional.' : 'La respuesta de la institución o la consulta pública (con ACA-01, también la evidencia). Es obligatorio para cerrar.'}
          </p>
          {SIN_ARCHIVOS(v.aca_soporte)
            ? (!SIN_SOPORTE_OBLIGATORIO.includes(ref) && <p className="text-xs font-semibold text-red-700">Falta soporte</p>)
            : <p className="text-xs font-semibold text-green-800">✓ Soporte cargado</p>}
          <InputPorTipo tipoDato="ARCHIVO" value={v.aca_soporte} onChange={x => poner('aca_soporte', x)} idServicioSubproceso={idServicioSubproceso} onError={onError} disabled={soloLectura} />
        </div>
      </section>
    </div>
  )
}
