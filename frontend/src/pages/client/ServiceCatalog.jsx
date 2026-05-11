import { useEffect, useState } from 'react'
import catalogoService from '../../services/catalogoService'

const META_CATEGORIA = {
  PRUEBAS_CONFIABILIDAD: {
    titulo: 'Pruebas de Confiabilidad',
    icono: '🔍',
    borde: 'border-blue-200',
    fondo: 'bg-blue-50',
    badge: 'bg-blue-100 text-blue-700',
  },
  ESTUDIOS_SEGURIDAD: {
    titulo: 'Estudios de Seguridad',
    icono: '🛡️',
    borde: 'border-indigo-200',
    fondo: 'bg-indigo-50',
    badge: 'bg-indigo-100 text-indigo-700',
  },
  HOJA_VIDA: {
    titulo: 'Hoja de Vida',
    icono: '📄',
    borde: 'border-green-200',
    fondo: 'bg-green-50',
    badge: 'bg-green-100 text-green-700',
  },
  OTROS: {
    titulo: 'Otros Servicios',
    icono: '✨',
    borde: 'border-purple-200',
    fondo: 'bg-purple-50',
    badge: 'bg-purple-100 text-purple-700',
  },
}

const ORDEN_CATEGORIAS = ['PRUEBAS_CONFIABILIDAD', 'ESTUDIOS_SEGURIDAD', 'HOJA_VIDA', 'OTROS']

function formatearPrecio(precio) {
  if (!precio) return 'Precio a convenir'
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    minimumFractionDigits: 0,
  }).format(precio)
}

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
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
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
    if (!acc[s.categoria]) acc[s.categoria] = []
    acc[s.categoria].push(s)
    return acc
  }, {})

  return (
    <div className="space-y-8">
      <h2 className="text-xl font-bold text-gray-800">Catálogo de Servicios</h2>

      {ORDEN_CATEGORIAS.map((cat) => {
        const items = porCategoria[cat]
        if (!items?.length) return null
        const meta = META_CATEGORIA[cat] ?? {
          titulo: cat, icono: '📋',
          borde: 'border-gray-200', fondo: 'bg-gray-50', badge: 'bg-gray-100 text-gray-700',
        }

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
                  key={s.idCatalogo}
                  className={`rounded-xl border p-5 transition-shadow hover:shadow-md ${meta.borde} ${meta.fondo}`}
                >
                  <h4 className="font-semibold text-gray-800 mb-1">{s.nombre}</h4>
                  {s.descripcion && (
                    <p className="text-sm text-gray-600 mb-3 line-clamp-2">{s.descripcion}</p>
                  )}
                  <p className="text-sm font-semibold text-indigo-700">
                    {formatearPrecio(s.precioBase)}
                  </p>
                </div>
              ))}
            </div>
          </section>
        )
      })}
    </div>
  )
}
