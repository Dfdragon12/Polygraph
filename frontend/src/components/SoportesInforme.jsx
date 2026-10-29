// Paso "Soportes del informe" del formulario: lista cada archivo adjunto en los campos y tablas
// (imágenes, PDF, otros) y dice cuáles SALDRÁN en el informe. Es la vista previa de lo que arma el
// backend (SoportesAnexoWord): en la celda va el enlace "Ver soporte N" y la imagen completa va al
// anexo "Soportes de las consultas" del final. Imágenes jpg/png y cada página de un PDF salen;
// cualquier otro tipo de archivo solo se menciona por su nombre.
import { useEffect, useState } from 'react'
import formulariosService from '../services/formulariosService'
import Paginacion, { usePaginacion } from './ui/Paginacion'

const nombreArchivo = (ruta) => {
  const segmento = ruta.split('/').pop()
  const idx = segmento.indexOf('_')
  return idx >= 0 ? segmento.slice(idx + 1) : segmento
}

const extension = (ruta) => (ruta.split('.').pop() ?? '').toLowerCase()

// El valor de una columna ARCHIVO es un array JSON de rutas (o una ruta suelta, formato antiguo).
const parseRutas = (valor) => {
  if (!valor) return []
  try {
    const p = JSON.parse(valor)
    return Array.isArray(p) ? p : [valor]
  } catch {
    return [valor]
  }
}

const tieneValor = (v) => v != null && String(v).trim() !== '' && String(v).trim() !== '—'

/** Qué pasará con un archivo en el informe: 'imagen' | 'pdf' | 'nombre'. */
export const destinoArchivo = (ruta) => {
  const ext = extension(ruta)
  if (ext === 'jpg' || ext === 'jpeg' || ext === 'png') return 'imagen'
  if (ext === 'pdf') return 'pdf'
  return 'nombre'
}

/** Todos los soportes del formulario, en el mismo orden en que el informe los numera: secciones en
 *  orden, campos por orden de columna, filas de arriba abajo. Lo quitado (secciones omitidas)
 *  no sale. */
export function listarSoportes(secciones, omitidas) {
  const lista = []
  for (const sec of secciones ?? []) {
    if (omitidas?.has(sec.idSeccion)) continue
    const columnas = [...(sec.columnas ?? [])].sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0))
    const columnasArchivo = columnas.filter(c => c.tipoDato === 'ARCHIVO' && !c.automatico)
    if (columnasArchivo.length === 0) continue
    const agregar = (valores, donde, titulo) => {
      columnasArchivo.forEach(c => {
        parseRutas(valores?.[c.idColumna]).forEach(ruta => {
          lista.push({
            ruta, archivo: nombreArchivo(ruta), destino: destinoArchivo(ruta),
            origen: `${sec.nombre || 'Sección'} › ${c.nombre || c.clave}${donde ? ` (${donde})` : ''}`,
            titulo: titulo || c.nombre || c.clave,
          })
        })
      })
    }
    if (sec.tipo === 'CAMPOS') {
      agregar(sec.valores, null, null)
    } else {
      ;(sec.filas ?? []).forEach((fila, i) => {
        // Mismo criterio del informe: la etiqueta de la fila o su primer texto diligenciado.
        const primero = columnas.find(c => c.tipoDato !== 'ARCHIVO' && c.tipoDato !== 'BOOLEANO' && tieneValor(fila.valores?.[c.idColumna]))
        const titulo = fila.etiqueta?.trim() || (primero ? String(fila.valores[primero.idColumna]).trim() : null)
        agregar(fila.valores, `fila ${i + 1}${titulo ? `: ${titulo}` : ''}`, titulo)
      })
    }
  }
  return lista
}

function Miniatura({ idServicioSubproceso, ruta, destino }) {
  const [url, setUrl] = useState(null)
  useEffect(() => {
    let u = null
    let vivo = true
    if (destino === 'imagen') {
      formulariosService.descargarArchivo(idServicioSubproceso, ruta).then(blob => {
        if (!vivo) return
        u = window.URL.createObjectURL(blob)
        setUrl(u)
      }).catch(() => {})
    }
    return () => { vivo = false; if (u) window.URL.revokeObjectURL(u) }
  }, [idServicioSubproceso, ruta, destino])

  const abrir = async () => {
    try {
      const blob = await formulariosService.descargarArchivo(idServicioSubproceso, ruta)
      const href = window.URL.createObjectURL(blob)
      window.open(href, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(href), 60000)
    } catch { /* el botón no hace nada si el archivo ya no está */ }
  }

  return (
    <button type="button" onClick={abrir} aria-label={`Abrir ${nombreArchivo(ruta)}`}
      className="h-14 w-14 flex items-center justify-center rounded-lg border border-gray-200 bg-gray-50 overflow-hidden text-lg">
      {url ? <img src={url} alt="" className="h-full w-full object-cover" /> : destino === 'pdf' ? '📕' : '📄'}
    </button>
  )
}

const ETIQUETAS = {
  imagen: { texto: 'Sí · imagen completa en el anexo', cls: 'bg-green-50 text-green-700 ring-green-200' },
  pdf: { texto: 'Sí · cada página del PDF en el anexo', cls: 'bg-green-50 text-green-700 ring-green-200' },
  nombre: { texto: 'No · solo se menciona el nombre', cls: 'bg-amber-50 text-amber-800 ring-amber-200' },
}

export default function SoportesInforme({ idServicioSubproceso, secciones, omitidas }) {
  const soportes = listarSoportes(secciones, omitidas)
  const pag = usePaginacion(soportes, soportes.length)
  const salen = soportes.filter(s => s.destino !== 'nombre').length
  const soloNombre = soportes.length - salen

  return (
    <section className="bg-white border border-gray-200 border-l-4 border-l-sky-400 rounded-2xl shadow-sm p-4">
      <h2 className="text-sm font-semibold text-gray-800">Soportes del informe</h2>
      <p className="text-xs text-gray-500 mt-1">
        En las tablas y campos del informe, cada soporte aparece como un enlace «Ver soporte N»; la imagen completa va junta
        en el anexo «Soportes de las consultas», al final del documento (Word y PDF).
      </p>

      {soportes.length === 0 ? (
        <p className="mt-4 text-sm text-gray-500">Todavía no hay archivos adjuntos en los campos del formulario.</p>
      ) : (
        <>
          <p className="mt-3 text-sm text-gray-700" role="status">
            <strong>{salen}</strong> {salen === 1 ? 'archivo saldrá' : 'archivos saldrán'} en el informe
            {soloNombre > 0 && <> · <strong>{soloNombre}</strong> solo se {soloNombre === 1 ? 'menciona' : 'mencionan'} por su nombre</>}.
          </p>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-sm table-stack">
              <thead>
                <tr className="text-left text-xs text-gray-500 border-b border-gray-100">
                  <th className="py-2 px-3 font-medium">N°</th>
                  <th className="py-2 px-3 font-medium">Vista</th>
                  <th className="py-2 px-3 font-medium">Archivo</th>
                  <th className="py-2 px-3 font-medium">De dónde viene</th>
                  <th className="py-2 px-3 font-medium">¿Sale en el informe?</th>
                </tr>
              </thead>
              <tbody>
                {pag.visibles.map((s, i) => (
                  <tr key={`${s.ruta}-${pag.desde + i}`} className="border-b border-gray-50 align-middle">
                    <td className="py-2 px-3 text-gray-500">{pag.desde + i + 1}</td>
                    <td className="py-2 px-3"><Miniatura idServicioSubproceso={idServicioSubproceso} ruta={s.ruta} destino={s.destino} /></td>
                    <td className="py-2 px-3 text-gray-800 break-words">{s.archivo}</td>
                    <td className="py-2 px-3 text-gray-600 break-words">{s.origen}</td>
                    <td className="py-2 px-3">
                      <span className={`inline-block text-xs font-medium px-2 py-1 rounded-full ring-1 ${ETIQUETAS[s.destino].cls}`}>
                        {ETIQUETAS[s.destino].texto}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pag.pagina} total={pag.total} porPagina={pag.porPagina} onChange={pag.setPagina} />
          <p className="mt-3 text-xs text-gray-400">
            El número definitivo («Soporte N») se asigna al generar el informe; un PDF ocupa un número por cada página.
          </p>
        </>
      )}
    </section>
  )
}
