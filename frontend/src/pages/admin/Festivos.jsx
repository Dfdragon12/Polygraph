import { useId, useState, useEffect, useCallback } from 'react'
import festivoService from '../../services/festivoService'
import Toast from '../../components/Toast'
import Paginacion, { usePaginacion } from '../../components/ui/Paginacion'

const ANIO_ACTUAL = new Date().getFullYear()
const ANIOS = [ANIO_ACTUAL - 1, ANIO_ACTUAL, ANIO_ACTUAL + 1, ANIO_ACTUAL + 2]

const fechaLarga = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('es-CO', { weekday: 'long', day: '2-digit', month: 'long' })

export default function Festivos() {
  const idCampos = useId()
  const [anio, setAnio] = useState(ANIO_ACTUAL)
  const [festivos, setFestivos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [toast, setToast] = useState(null)
  const [nuevaFecha, setNuevaFecha] = useState('')
  const [nuevaDescripcion, setNuevaDescripcion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const pag = usePaginacion(festivos, anio)

  const cargar = useCallback(() => {
    setCargando(true)
    festivoService.listar(anio)
      .then(setFestivos)
      .catch(() => setToast({ mensaje: 'No se pudieron cargar los festivos.', tipo: 'error' }))
      .finally(() => setCargando(false))
  }, [anio])

  useEffect(() => { cargar() }, [cargar])

  const cambiar = async (fecha, activo, descripcion) => {
    setGuardando(true)
    try {
      await festivoService.cambiar(fecha, activo, descripcion)
      cargar()
      setToast({ mensaje: activo ? 'Día no hábil activado.' : 'Día no hábil desactivado.', tipo: 'exito' })
      return true
    } catch (e) {
      setToast({ mensaje: e.response?.data?.message ?? 'No se pudo guardar el cambio.', tipo: 'error' })
      return false
    } finally {
      setGuardando(false)
    }
  }

  const agregar = async (e) => {
    e.preventDefault()
    if (!nuevaFecha) return
    if (await cambiar(nuevaFecha, true, nuevaDescripcion)) {
      if (Number(nuevaFecha.slice(0, 4)) !== anio) setAnio(Number(nuevaFecha.slice(0, 4)))
      setNuevaFecha('')
      setNuevaDescripcion('')
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Festivos y días no hábiles</h2>
          <p className="text-sm text-gray-500 mt-0.5 max-w-2xl">
            Los plazos de entrega y el retraso se cuentan en días hábiles: sin sábados, domingos ni estos días. Los festivos de Colombia se calculan
            solos cada año; aquí puedes desactivar uno o agregar un día no laborable propio. Los cambios aplican a los cálculos desde ya; las fechas
            de entrega que ya se fijaron no se recalculan.
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-600">
          Año
          <select value={anio} onChange={e => setAnio(Number(e.target.value))}
            className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500">
            {ANIOS.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
        </label>
      </div>

      <form onSubmit={agregar} className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-4 flex flex-col sm:flex-row sm:items-end gap-3">
        <div>
          <label htmlFor={`${idCampos}-fecha`} className="block text-xs font-medium text-gray-600 mb-1">Agregar día no laborable</label>
          <input id={`${idCampos}-fecha`} type="date" value={nuevaFecha} onChange={e => setNuevaFecha(e.target.value)} required
            className="h-11 rounded-lg border border-gray-200 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div className="flex-1">
          <label htmlFor={`${idCampos}-desc`} className="block text-xs font-medium text-gray-600 mb-1">Descripción</label>
          <input id={`${idCampos}-desc`} type="text" value={nuevaDescripcion} onChange={e => setNuevaDescripcion(e.target.value)} required maxLength={150}
            placeholder="Ej.: Cierre de fin de año de la empresa"
            className="h-11 w-full rounded-lg border border-gray-200 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <button type="submit" disabled={guardando}
          className="h-11 px-4 text-sm font-semibold text-white bg-primary-600 rounded-lg shadow-sm hover:bg-primary-700 disabled:opacity-50 transition-colors">
          Agregar
        </button>
      </form>

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center h-40">
            <div className="animate-spin rounded-full h-7 w-7 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : (
          <>
            <table className="w-full text-sm table-stack">
              <thead>
                <tr className="bg-gray-50 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                  <th className="px-3 py-3">Fecha</th>
                  <th className="px-3 py-3">Festivo</th>
                  <th className="px-3 py-3">Origen</th>
                  <th className="px-3 py-3">Cuenta como día no hábil</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pag.visibles.map(f => (
                  <tr key={f.fecha} className={f.activo ? '' : 'text-gray-400'}>
                    <td data-label="Fecha" className="px-3 py-3 capitalize">{fechaLarga(f.fecha)}</td>
                    <td data-label="Festivo" className="px-3 py-3">{f.nombre}</td>
                    <td data-label="Origen" className="px-3 py-3">{f.origen === 'AUTO' ? 'Ley (calculado)' : 'Agregado'}</td>
                    <td data-label="Cuenta como día no hábil" className="px-3 py-3">
                      <button type="button" role="switch" aria-checked={f.activo} aria-label={`${f.nombre}: ${f.activo ? 'activo' : 'desactivado'}`}
                        disabled={guardando} onClick={() => cambiar(f.fecha, !f.activo, f.nombre)}
                        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors disabled:opacity-50 ${f.activo ? 'bg-primary-600' : 'bg-gray-300'}`}>
                        <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${f.activo ? 'translate-x-6' : 'translate-x-1'}`} />
                      </button>
                    </td>
                  </tr>
                ))}
                {pag.visibles.length === 0 && (
                  <tr><td colSpan={4} className="px-3 py-10 text-center text-gray-400">No hay festivos para este año.</td></tr>
                )}
              </tbody>
            </table>
            <Paginacion pagina={pag.pagina} total={pag.total} porPagina={pag.porPagina} onChange={pag.setPagina} />
          </>
        )}
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
