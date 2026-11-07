import { useEffect } from 'react'
import MarcoFormato from './MarcoFormato'
import ConsultaLaboral from './ConsultaLaboral'
import AvisoCargo from './AvisoCargo'
import { DatosLaboral as DatosEvaluado } from './TemasLaboral'
import EstudioAcademica from './EstudioAcademica'
import { SugerenciasAcademica, armarBorradorAcademica } from './RevisionAcademica'
import { EducacionCriterio, accesoEstudio, ponerSeleccion } from './TemasAcademica'
import { CRITERIO_POR_DEFECTO, estudioSegunCriterio } from './reglasAcademica'
import { Tarjeta } from '../poligrafista/componentesHoja'
import { ConceptoAnalista } from './RevisionAntecedentes'
import { FASES, TODOS_LOS_TEMAS, partesDelTema, estadoDeTemas, faltantesDeAcademica } from './fasesAcademica'

// AC-01 / AC-02 · Verificación académica en 3 fases y 4 temas, como el formato en Word. El marco (encabezado, fases, hoja, pie, guardado, faltantes y envío) es
// MarcoFormato; aquí solo está lo propio de este servicio.

const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''
const CATALOGO = /^(ACA|ALR-ACA)-/

// Lo que el evaluado declaró en su hoja de vida (tabla fija): solo lectura, el analista no la edita.
const estudiosDe = (inicial) => (inicial.tablasFijas ?? []).find(t => t.clave === 'educacion')?.filas ?? []
const delExterior = (e) => /^s[ií]$/i.test(String(e.en_exterior ?? ''))

function EstudiosDeclarados({ filas, sinEstudios }) {
  if (filas.length === 0) {
    return <p className="text-sm text-gray-700">{sinEstudios ? 'El evaluado declaró que no finalizó el bachillerato.' : 'El evaluado no registró estudios en su hoja de vida.'}</p>
  }
  return (
    <div className="overflow-x-auto" data-clave="estudios_declarados">
      <h3 className="text-sm font-bold text-gray-900 mb-1">Información académica declarada</h3>
      <p className="text-xs text-gray-700 mb-2">Lo que registró el evaluado en su hoja de vida. Solo lectura: el analista no la modifica.</p>
      <table className="w-full text-sm border border-gray-300">
        <thead className="bg-gray-100 text-xs text-gray-700">
          <tr>{['Institución', 'Nivel', 'Título', 'Finalización'].map(t => <th key={t} scope="col" className="text-left p-2 border-b border-gray-300">{t}</th>)}</tr>
        </thead>
        <tbody>
          {filas.map(e => (
            <tr key={e.id} className="border-b border-gray-200 align-top bg-gray-50">
              <td className="p-2 break-words">{e.institucion || '—'}</td>
              <td className="p-2 break-words">{e.nivel || '—'}</td>
              <td className="p-2 break-words">{e.titulo || '—'}</td>
              <td className="p-2 whitespace-nowrap">{e.fecha_fin || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PanelCaso({ filas, nota }) {
  return (
    <div className="space-y-3">
      <Tarjeta key={`ev-${filas.length > 0}`} titulo="Lo que registró el evaluado" vacia={filas.length === 0}>
        <ul className="space-y-3">
          {filas.map(e => (
            <li key={e.id}>
              <p className="text-gray-900 font-semibold break-words">{e.titulo || e.nivel || '—'}</p>
              <p className="text-xs text-gray-700 break-words">{[e.nivel, e.institucion, e.ciudad, delExterior(e) ? `En el exterior${tiene(e.pais_exterior) ? ` (${e.pais_exterior})` : ''}` : null].filter(tiene).join(' · ')}</p>
              <p className="text-xs text-gray-700 break-words">{[`${e.fecha_inicio || '—'} → ${e.fecha_fin || '—'}`, e.estado].filter(tiene).join(' · ')}</p>
            </li>
          ))}
        </ul>
      </Tarjeta>
      <Tarjeta key={`nota-${!!nota}`} titulo="Observación de quien coordina" vacia={!nota} tono="nota">
        <p className="text-gray-900 whitespace-pre-line break-words">{nota}</p>
      </Tarjeta>
      <Tarjeta titulo="Consulta interna" abierta>
        <ConsultaLaboral patron={CATALOGO} titulo="Catálogo de referencias · Verificación académica" />
      </Tarjeta>
    </div>
  )
}

// AC-02 · El criterio llega «Último estudio finalizado» y, con ese criterio, el estudio se selecciona solo. Se escribe una vez, al abrir; después manda el analista.
function usarEfectosAcademica(c) {
  const estudios = estudiosDe(c.inicial)
  const a = accesoEstudio(c.secciones, c.editarCampo)
  const criterio = a.val('aca_criterio')
  const seleccion = a.val('aca_estudio_sel')
  useEffect(() => {
    if (c.soloLectura || !a.existe) return
    if (!tiene(criterio)) { a.poner('aca_criterio', CRITERIO_POR_DEFECTO); return }
    if (criterio === CRITERIO_POR_DEFECTO && !tiene(seleccion)) {
      const r = estudioSegunCriterio(criterio, estudios, c.inicial.sinEstudiosDeclarado)
      if (r.estudio) ponerSeleccion(a, r.estudio)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [criterio, seleccion, c.soloLectura, a.existe])
}

export default function FormatoAcademica({ inicial, idServicioSubproceso }) {
  const filas = estudiosDe(inicial)
  const configuracion = {
    fases: FASES, temas: TODOS_LOS_TEMAS, partesDelTema, titulo: 'Verificación académica', archivo: 'Academica',
    estadoDeTemas: (s) => estadoDeTemas(s, filas.length),
    faltantesDe: (s) => faltantesDeAcademica(s, filas.length),
  }
  const renderPanel = () => <PanelCaso filas={filas} nota={inicial.observacionesAsignacion} />
  const renderEncabezado = (c) => (!c.soloLectura ? <AvisoCargo cargo={inicial.cargo} onAvisar={c.avisarGestor} clave="ACA_CARGO_NO_COINCIDE" /> : null)

  const renderTema = (c) => (
    <>
      {c.tema.vista === 'datos' && (
        <DatosEvaluado secciones={c.secciones} datosCandidato={inicial.datosCandidato} cargoSolicitud={inicial.cargo} nombreProceso={inicial.nombreProceso}
          criticidad={c.criticidad} soloLectura={c.soloLectura} onAvisar={c.avisarGestor} claveNovedad="ACA_DATO_NO_COINCIDE" />
      )}
      {c.tema.vista === 'educacion' && (
        <>
          <EstudiosDeclarados filas={filas} sinEstudios={!!inicial.sinEstudiosDeclarado} />
          <EducacionCriterio secciones={c.secciones} estudios={filas} sinEstudios={!!inicial.sinEstudiosDeclarado} soloLectura={c.soloLectura} onCampo={c.editarCampo} />
        </>
      )}
      {c.tema.vista === 'estudio' && (
        <EstudioAcademica secciones={c.secciones} estudios={filas} soloLectura={c.soloLectura} idServicioSubproceso={idServicioSubproceso} onCampo={c.editarCampo}
          onCelda={c.editarCeldaFila} onAgregar={c.agregarFila} onQuitar={c.quitarFila} onError={c.error} />
      )}
      {!c.tema.vista && c.partes.map((p, i) => (
        <div key={`${p.seccion.idSeccion}-${i}`}>
          {p.seccion.clave === 'concepto_analista'
            ? <ConceptoAnalista secciones={c.secciones} nombreProfesional={inicial.nombreProfesionalEncargado} soloLectura={c.soloLectura} onCampo={c.editarCampo} armarBorrador={armarBorradorAcademica} />
            : (
              <>
                {p.seccion.clave === 'informativos' && <div className="mb-3"><SugerenciasAcademica secciones={c.secciones} soloLectura={c.soloLectura} onAgregar={c.agregarFila} /></div>}
                <c.Parte {...c.propsParte(p)} />
              </>
            )}
        </div>
      ))}
      {!c.tema.informe && !c.tema.vista && c.partes.length === 0 && <p className="text-gray-700">Este tema no tiene campos en la plantilla.</p>}
    </>
  )

  return (
    <MarcoFormato inicial={inicial} idServicioSubproceso={idServicioSubproceso} configuracion={configuracion} renderTema={renderTema} renderPanel={renderPanel}
      renderEncabezado={renderEncabezado} usarEfectos={usarEfectosAcademica} />
  )
}
