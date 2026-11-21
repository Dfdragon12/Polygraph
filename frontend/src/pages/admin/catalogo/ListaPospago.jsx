import { useEffect, useState } from 'react'
import api from '../../../services/api'
import { formatearPrecio } from '../../../utils/catalogoDisplay'

/** Pestaña «Lista pospago» (TP-11): precio pospago de todos los servicios activos, su origen, IVA, si se ofrece y el margen. Exportable a Excel. */
export default function ListaPospago() {
  const [filas, setFilas] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.get('/catalogo/pospago/lista').then((r) => setFilas(r.data)).catch(() => setError('No se pudo cargar la lista pospago.'))
  }, [])

  const exportar = async () => {
    try {
      const r = await api.get('/catalogo/pospago/lista/exportar', { responseType: 'blob' })
      const url = URL.createObjectURL(r.data)
      const a = document.createElement('a')
      a.href = url; a.download = 'lista-pospago.xlsx'; a.click()
      URL.revokeObjectURL(url)
    } catch { setError('No se pudo exportar la lista.') }
  }

  if (error) return <p role="alert" className="text-sm text-red-700">{error}</p>
  if (!filas) return <p className="text-sm text-gray-500">Cargando…</p>

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-gray-600">Lo que paga un cliente pospago por cada servicio. Se ve solo aquí y en la lista de cada cliente pospago.</p>
        <button type="button" onClick={exportar}
          className="shrink-0 inline-flex items-center gap-2 border border-gray-200 text-gray-700 hover:bg-gray-50 text-sm font-medium px-4 py-2 rounded-lg">
          Exportar a Excel
        </button>
      </div>
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-x-auto">
        <table className="table-stack w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              <th className="px-3 py-3 text-left">Servicio</th>
              <th className="px-3 py-3 text-right">Precio pospago</th>
              <th className="px-3 py-3 text-left">Origen</th>
              <th className="px-3 py-3 text-right">Con IVA</th>
              <th className="px-3 py-3 text-left">Ofrecido</th>
              <th className="px-3 py-3 text-right">Margen</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {filas.map((f) => (
              <tr key={f.idProceso}>
                <td className="px-3 py-2.5 font-medium text-gray-800">
                  {f.servicio}
                  {f.problema && <span className="block text-xs font-normal text-red-700">{f.problema}</span>}
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums">{f.precio != null ? formatearPrecio(f.precio) : '—'}</td>
                <td className="px-3 py-2.5 text-gray-600">{f.origen}</td>
                <td className="px-3 py-2.5 text-right tabular-nums">{f.precioConIva != null ? formatearPrecio(f.precioConIva) : '—'}</td>
                <td className="px-3 py-2.5">{f.ofrecido ? 'Sí' : 'No'}</td>
                <td className={`px-3 py-2.5 text-right tabular-nums ${f.margenBajo ? 'font-semibold text-red-700' : 'text-gray-700'}`}>
                  {f.margenPorcentaje != null ? `${Number(f.margenPorcentaje).toLocaleString('es-CO')} %` : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
