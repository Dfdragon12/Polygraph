import { useEffect, useState } from 'react'
import catalogoService from '../../services/catalogoService'
import { metaCategoria, ordenarCategorias, formatearPrecio } from '../../utils/catalogoDisplay'

export default function ServiceCatalog() {
  const [servicios, setServicios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    catalogoService.listarServicios()
      .then(setServicios)
      .catch(() => setError('No se pudo cargar el catálogo. Intenta nuevamente.'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-red-500 text-sm">{error}</p>
      </div>
    )
  }

  const porCategoria = servicios.reduce((acc, s) => {
    if (!acc[s.clasificacion]) acc[s.clasificacion] = []
    acc[s.clasificacion].push(s)
    return acc
  }, {})

  const categoriasOrdenadas = ordenarCategorias(porCategoria)

  return (
    <div className="space-y-8">
      <h2 className="text-xl font-bold text-gray-800">Catálogo de Servicios</h2>

      {categoriasOrdenadas.map((cat) => {
        const items = porCategoria[cat]
        if (!items?.length) return null
        const meta = metaCategoria(cat, items[0]?.codigoClasificacion)

        return (
          <section key={cat}>
            <div className="flex items-center gap-2 mb-4">
              <span className="text-xl">{meta.icono}</span>
              <h3 className="text-lg font-semibold text-gray-700">{meta.titulo}</h3>
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${meta.badge}`}>
                {items.length} {items.length === 1 ? 'servicio' : 'servicios'}
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {items.map((s) => (
                <div
                  key={s.idProceso}
                  className={`rounded-xl border p-5 transition-shadow hover:shadow-md ${meta.borde} ${meta.fondo}`}
                >
                  <h4 className="font-semibold text-gray-800 mb-1">{s.nombreProceso}</h4>
                  {s.descripcion && (
                    <p className="text-sm text-gray-600 mb-3 line-clamp-2">{s.descripcion}</p>
                  )}
                  <div className="flex items-center justify-between flex-wrap gap-2 mt-auto">
                    <p className="text-sm font-semibold text-primary-700">
                      {formatearPrecio(s.valor)}
                    </p>
                    {s.diasHabilesEntrega != null && (
                      <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-white/70 border border-sky-200 text-sky-700">
                        <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        {s.diasHabilesEntrega} día{s.diasHabilesEntrega !== 1 ? 's' : ''} hábil{s.diasHabilesEntrega !== 1 ? 'es' : ''}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )
      })}
    </div>
  )
}
