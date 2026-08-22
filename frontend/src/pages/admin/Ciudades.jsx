import { useState, useEffect, useCallback, useMemo } from 'react'
import ciudadService from '../../services/ciudadService'
import Toast from '../../components/Toast'

const NIVELES_CIUDAD = [
  { valor: 'PRINCIPAL',            etiqueta: 'Principal',                        color: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  { valor: 'INTERMEDIA',           etiqueta: 'Intermedia / municipio principal',  color: 'bg-amber-50 text-amber-700 border-amber-200' },
  { valor: 'MUNICIPIO_SECUNDARIO', etiqueta: 'Municipio secundario',              color: 'bg-rose-50 text-rose-700 border-rose-200' },
]

function badgeNivel(nivel) {
  return NIVELES_CIUDAD.find(n => n.valor === nivel) ?? { etiqueta: nivel ?? '—', color: 'bg-gray-100 text-gray-500 border-gray-200' }
}

function BarraFiltros({ busqueda, onBusqueda, filtro, onFiltro, total, pendientes }) {
  return (
    <div className="flex flex-wrap items-center gap-3 mb-3">
      <input type="search" value={busqueda} onChange={e => onBusqueda(e.target.value)}
        placeholder="Buscar por ciudad, departamento o código DANE…"
        className="flex-1 min-w-[220px] max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      <div className="flex gap-1">
        {[
          { key: 'todas',      label: 'Todas',                  count: total      },
          { key: 'pendientes', label: 'Pendientes por confirmar', count: pendientes },
        ].map(f => (
          <button key={f.key} onClick={() => onFiltro(f.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              filtro === f.key
                ? f.key === 'pendientes' ? 'bg-amber-100 text-amber-700' : 'bg-primary-100 text-primary-700'
                : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
            }`}>
            {f.label}<span className="ml-1.5 font-normal opacity-70">{f.count}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

function TablaCiudades({ ciudades, cargando, onClasificar, onConfirmar }) {
  if (cargando) return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  if (!ciudades.length) return <div className="text-center py-16 text-gray-400 text-sm">No hay ciudades que coincidan con el filtro.</div>

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-100">
        <thead className="bg-gray-50">
          <tr>
            {['Ciudad / Municipio', 'Departamento', 'Cód. DANE ciudad', 'Cód. DANE depto', 'Nivel logístico', 'Estado', ''].map(h => (
              <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {ciudades.map(c => {
            const badge = badgeNivel(c.nivelCiudad)
            return (
              <tr key={c.idCiudad} className={`hover:bg-gray-50 transition-colors ${c.pendienteConfirmacion ? 'bg-amber-50/40' : ''}`}>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{c.nombreCiudad}</td>
                <td className="px-4 py-3 text-sm text-gray-500">{c.departamento}</td>
                <td className="px-4 py-3 text-sm text-gray-400 font-mono">{c.codigoDaneCiudad ?? '—'}</td>
                <td className="px-4 py-3 text-sm text-gray-400 font-mono">{c.codigoDaneDepto ?? '—'}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${badge.color}`}>
                    {badge.etiqueta}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {c.pendienteConfirmacion ? (
                    <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-700 border border-amber-200">
                      <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                      </svg>
                      Pendiente por confirmar
                    </span>
                  ) : (
                    <span className="text-xs text-gray-400">Confirmada</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2 flex-wrap">
                    {c.pendienteConfirmacion && (
                      <button onClick={() => onConfirmar(c)}
                        className="text-xs font-medium px-3 py-1.5 rounded-lg border border-emerald-200 text-emerald-700 hover:bg-emerald-50 whitespace-nowrap">
                        Aceptar
                      </button>
                    )}
                    <select value={c.nivelCiudad ?? ''} onChange={e => onClasificar(c, e.target.value)}
                      className="border border-gray-200 rounded-lg px-2.5 py-1.5 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                      {NIVELES_CIUDAD.map(n => (
                        <option key={n.valor} value={n.valor}>{n.etiqueta}</option>
                      ))}
                    </select>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default function Ciudades() {
  const [ciudades, setCiudades] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError]       = useState(null)
  const [toast, setToast]       = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [filtro, setFiltro]     = useState('todas')

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      setCiudades(await ciudadService.listar())
    } catch { setError('Error al cargar ciudades.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const pendientes = useMemo(() => ciudades.filter(c => c.pendienteConfirmacion).length, [ciudades])

  const ciudadesVisibles = useMemo(() => {
    let lista = ciudades
    if (filtro === 'pendientes') lista = lista.filter(c => c.pendienteConfirmacion)
    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(c =>
        c.nombreCiudad.toLowerCase().includes(q) ||
        c.departamento.toLowerCase().includes(q) ||
        (c.codigoDaneCiudad ?? '').toLowerCase().includes(q) ||
        (c.codigoDaneDepto ?? '').toLowerCase().includes(q))
    }
    return lista
  }, [ciudades, filtro, busqueda])

  const clasificar = async (ciudad, nivel) => {
    if (!nivel || nivel === ciudad.nivelCiudad) return
    try {
      const data = await ciudadService.clasificar(ciudad.idCiudad, nivel)
      setCiudades(prev => prev.map(c => c.idCiudad === ciudad.idCiudad ? data : c))
      setToast({ mensaje: `"${ciudad.nombreCiudad}" reclasificada correctamente`, tipo: 'exito' })
    } catch { setError('Error al clasificar la ciudad.') }
  }

  const confirmar = async (ciudad) => {
    try {
      const data = await ciudadService.confirmar(ciudad.idCiudad)
      setCiudades(prev => prev.map(c => c.idCiudad === ciudad.idCiudad ? data : c))
      setToast({ mensaje: `"${ciudad.nombreCiudad}" confirmada en ${badgeNivel(data.nivelCiudad).etiqueta}`, tipo: 'exito' })
    } catch { setError('Error al confirmar la ciudad.') }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Ciudades</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            Clasifica cada ciudad por su nivel logístico para tarifar servicios con desplazamiento
          </p>
        </div>
      </div>

      {pendientes > 0 && (
        <div className="mb-4 flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3.5">
          <svg className="h-5 w-5 text-amber-500 flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
          </svg>
          <div>
            <p className="text-sm font-semibold text-amber-800">
              {pendientes} ciudad{pendientes !== 1 ? 'es' : ''} pendiente{pendientes !== 1 ? 's' : ''} por confirmar
            </p>
            <p className="text-xs text-amber-600 mt-0.5">
              Se crearon automáticamente cuando un cliente o candidato registró una ciudad nueva. Entraron como
              "Municipio secundario" (el nivel más caro, para no perder margen) — revísalas y dale "Aceptar" si
              está bien, o cámbiala si corresponde a otro nivel. Ej: si alguien crea "Soacha", queda en
              Municipio secundario hasta que tú confirmes o la reclasifiques.
            </p>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-3 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      <BarraFiltros
        busqueda={busqueda} onBusqueda={setBusqueda}
        filtro={filtro} onFiltro={setFiltro}
        total={ciudades.length} pendientes={pendientes}
      />

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <TablaCiudades ciudades={ciudadesVisibles} cargando={cargando} onClasificar={clasificar} onConfirmar={confirmar} />
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
