import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../../services/api'
import Paginacion from '../../components/ui/Paginacion'

const POR_PAGINA = 10

function IconRecibo() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3M3.75 6h16.5a1.5 1.5 0 011.5 1.5v9a1.5 1.5 0 01-1.5 1.5H3.75a1.5 1.5 0 01-1.5-1.5v-9a1.5 1.5 0 011.5-1.5z" />
    </svg>
  )
}

function BadgeMora({ mora }) {
  const cfg = {
    EN_MORA:    { cls: 'bg-red-100 text-red-700',   label: 'En mora' },
    SUSPENDIDO: { cls: 'bg-gray-200 text-gray-700',  label: 'Suspendido' },
  }[mora]
  if (!cfg) return null
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${cfg.cls}`}>{cfg.label}</span>
}

const FILTROS_TIPO = [
  { key: 'todos',   label: 'Todos' },
  { key: 'PREPAGO', label: 'Prepago' },
  { key: 'POSPAGO', label: 'Pospago' },
]

/** Selector de cliente para el módulo de Facturación — un solo sitio para ver el historial de
 * compras (prepago) o el crédito y las facturas (pospago) de cualquier cliente. */
export default function Facturacion() {
  const [clientes, setClientes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [filtroTipo, setFiltroTipo] = useState('todos')
  const [pagina, setPagina] = useState(1)

  useEffect(() => {
    api.get('/clientes')
      .then((res) => setClientes(res.data))
      .catch(() => setError('No se pudieron cargar los clientes.'))
      .finally(() => setCargando(false))
  }, [])

  const filtrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    return clientes.filter((c) => {
      if (filtroTipo !== 'todos' && c.tipoCliente !== filtroTipo) return false
      if (!q) return true
      return c.nombreDisplay?.toLowerCase().includes(q) || c.nit?.toLowerCase().includes(q) || c.emailPrincipal?.toLowerCase().includes(q)
    })
  }, [clientes, busqueda, filtroTipo])

  useEffect(() => { setPagina(1) }, [busqueda, filtroTipo])

  const clientesPagina = filtrados.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Facturación</h2>
        <p className="text-sm text-gray-500 mt-0.5">
          Busca un cliente para ver su historial de compras (prepago) o su crédito y facturas (pospago).
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-4">
        <input value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por nombre, NIT o email..."
          className="flex-1 min-w-[220px] border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        <div className="flex gap-1">
          {FILTROS_TIPO.map((f) => (
            <button key={f.key} onClick={() => setFiltroTipo(f.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                filtroTipo === f.key ? 'bg-primary-100 text-primary-700' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {cargando ? (
        <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
      ) : error ? (
        <div className="text-center py-16 text-red-500 text-sm">{error}</div>
      ) : !filtrados.length ? (
        <div className="text-center py-16 text-gray-400 text-sm">No hay clientes que coincidan con el filtro.</div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Cliente</th>
                  <th className="px-4 py-3 text-left">Tipo</th>
                  <th className="px-4 py-3 text-left">Email</th>
                  <th className="px-4 py-3 text-center">Facturación</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {clientesPagina.map((c) => (
                  <tr key={c.idCliente} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <p className="font-medium text-gray-800">{c.nombreDisplay}</p>
                      {c.nit && <p className="text-xs text-gray-400">NIT: {c.nit}{c.dv ? `-${c.dv}` : ''}</p>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className={`text-xs px-2 py-0.5 rounded font-medium ${
                          c.tipoCliente === 'PREPAGO' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'
                        }`}>
                          {c.tipoCliente === 'PREPAGO' ? 'Prepago' : 'Pospago'}
                        </span>
                        {c.pospago?.estadoMora && <BadgeMora mora={c.pospago.estadoMora} />}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap">{c.emailPrincipal ?? '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center">
                        <Link to={`/admin/facturacion/clientes/${c.idCliente}`} title="Ver facturación"
                          className="p-1.5 rounded-lg text-primary-600 hover:bg-primary-50 hover:text-primary-800 transition-colors">
                          <IconRecibo />
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} total={filtrados.length} porPagina={POR_PAGINA} onChange={setPagina} />
        </div>
      )}
    </div>
  )
}
