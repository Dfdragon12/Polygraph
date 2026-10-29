import { useCallback, useEffect, useState } from 'react'
import semaforoService from '../../services/semaforoService'
import { useCambios } from '../../hooks/useCambios'

// EL semáforo de estado, el mismo para todos los perfiles (SE-09): una tarjeta por estado, en el orden y con los colores del catálogo, con el conteo que
// calcula el servidor. Es un FILTRO: un servicio cuenta en cada estado donde tenga un subproceso, así que la suma puede superar el total. Sin barra que
// sume 100 % ni porcentajes. Clic = filtrar; clic en la activa = quitar el filtro.
//   filtros: { tiempo, idCliente, idResponsable, rol, idTipoProgreso } (opcionales, los mismos del servidor)
//   interno: muestra además cuántos subprocesos hay en cada estado (el cliente no)
//   textoCliente: usa la descripción pensada para el cliente bajo cada número
//   onDatos: avisa con la respuesta del servidor (así el Dashboard toma de aquí el total, sin pedirlo dos veces)
export default function SemaforoEstados({ seleccionado = '', onSeleccionar, filtros, interno = true, textoCliente = false, titulo = 'Estado de los servicios', refrescar = 0, onDatos }) {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(false)
  const clave = JSON.stringify(filtros ?? {})

  const cargar = useCallback(() => {
    semaforoService.resumen(filtros ?? {}).then(d => { setDatos(d); setError(false); onDatos?.(d) }).catch(() => setError(true))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave])

  useEffect(() => { cargar() }, [cargar, refrescar])
  useCambios(cargar)

  if (error && !datos) return <p role="alert" className="text-sm text-red-700 mb-4">No se pudo cargar el resumen por estado.</p>
  const porSubprocesos = datos?.unidad === 'SUBPROCESOS'
  const unidad = porSubprocesos ? 'subprocesos' : 'servicios'

  return (
    <section aria-label={titulo} className="mb-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2 mb-2">
        <h3 className="text-xs font-semibold text-gray-700 uppercase tracking-wider">{titulo}</h3>
        {datos && (
          <p className="text-xs text-gray-700">
            {datos.total} {datos.total === 1 ? unidad.slice(0, -1) : unidad}
            {!porSubprocesos && ' · un servicio cuenta en cada estado donde tenga un subproceso'}
          </p>
        )}
      </div>
      <div className="grid gap-3 grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7" role="group" aria-label="Filtrar por estado">
        {(datos?.estados ?? Array.from({ length: 4 }, (_, i) => ({ codigo: `c${i}`, cargando: true }))).map(e => {
          if (e.cargando) return <div key={e.codigo} className="h-24 rounded-xl border border-gray-200 bg-white animate-pulse" />
          const activo = seleccionado === e.codigo
          const cantidad = porSubprocesos ? e.subprocesos : e.servicios
          return (
            <button key={e.codigo} type="button" aria-pressed={activo} onClick={() => onSeleccionar?.(activo ? '' : e.codigo)}
              className={`text-left bg-white rounded-xl border p-4 shadow-sm transition-all hover:shadow-md ${activo ? 'ring-2' : 'border-gray-200'}`}
              style={activo ? { borderColor: e.color, '--tw-ring-color': e.color, backgroundColor: `${e.color}12` } : undefined}>
              <span className="flex items-center gap-2 mb-1.5">
                <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: e.color }} />
                <span className="text-xs font-semibold text-gray-800">{e.nombre}</span>
              </span>
              <span className="block text-3xl font-bold text-gray-900 tabular-nums">{cantidad}</span>
              <span className="block text-xs text-gray-700 mt-1 leading-snug">
                {textoCliente ? e.textoCliente : interno && !porSubprocesos ? `${e.subprocesos} subproceso${e.subprocesos === 1 ? '' : 's'}` : ''}
              </span>
            </button>
          )
        })}
      </div>
    </section>
  )
}
