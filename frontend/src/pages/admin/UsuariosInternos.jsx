import { parseFecha } from '../../utils/fechas'
import { useId, useState, useEffect, useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import api from '../../services/api'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'
import MenuAcciones from '../../components/ui/MenuAcciones'
import CiudadSelect from '../../components/CiudadSelect'
import catalogoService from '../../services/catalogoService'

// Minúsculas + sin tildes/diacríticos, para que la búsqueda encuentre "Jose" al escribir "José"
// (o al revés) sin tener que teclear el acento exacto.
const RANGO_DIACRITICOS = new RegExp('[' + String.fromCharCode(0x0300) + '-' + String.fromCharCode(0x036f) + ']', 'g')
const normalizarTexto = (s) => (s ?? '').toString().toLowerCase().normalize('NFD').replace(RANGO_DIACRITICOS, '')

const ROLES_OPCIONES = [
  { value: 'ADMIN_POLYGRAPH',  label: 'Administrador' },
  { value: 'GESTOR',           label: 'Gestor' },
  { value: 'ANALISTA_INTERNO', label: 'Analista Interno' },
  { value: 'PROGRAMADOR',      label: 'Programador' },
  { value: 'POLIGRAFISTA',     label: 'Poligrafista' },
  { value: 'VISITADOR',        label: 'Visitador' },
]

const ETIQUETA_ROL = {
  ADMIN_POLYGRAPH:  'Administrador',
  GESTOR:           'Gestor',
  ANALISTA_INTERNO: 'Analista Interno',
  PROGRAMADOR:      'Programador',
  POLIGRAFISTA:     'Poligrafista',
  VISITADOR:        'Visitador',
  ADMIN_CLIENTE:    'Admin Cliente',
  ANALISTA_CLIENTE: 'Analista Cliente',
}

// Tarjetas de filtro por rol — el color de cada campo va literal (no interpolado) para que
// Tailwind lo detecte en el build, mismo criterio que TARJETAS_ESTADO en Solicitudes.
const ROLES_FILTRO = [
  { value: 'ADMIN_POLYGRAPH',  label: 'Administrador',    corto: 'AD',
    border: 'border-violet-200',  badge: 'bg-violet-500',  texto: 'text-violet-600',  bgActivo: 'bg-violet-50/70',  ring: 'ring-violet-300' },
  { value: 'GESTOR',           label: 'Gestor',            corto: 'GE',
    border: 'border-blue-200',    badge: 'bg-blue-500',    texto: 'text-blue-600',    bgActivo: 'bg-blue-50/70',    ring: 'ring-blue-300' },
  { value: 'ANALISTA_INTERNO', label: 'Analista Interno',  corto: 'AI',
    border: 'border-emerald-200', badge: 'bg-emerald-500', texto: 'text-emerald-600', bgActivo: 'bg-emerald-50/70', ring: 'ring-emerald-300' },
  { value: 'PROGRAMADOR',      label: 'Programador',       corto: 'PR',
    border: 'border-amber-200',   badge: 'bg-amber-500',   texto: 'text-amber-600',   bgActivo: 'bg-amber-50/70',   ring: 'ring-amber-300' },
  { value: 'POLIGRAFISTA',     label: 'Poligrafista',      corto: 'PG',
    border: 'border-rose-200',    badge: 'bg-rose-500',    texto: 'text-rose-600',    bgActivo: 'bg-rose-50/70',    ring: 'ring-rose-300' },
  { value: 'VISITADOR',        label: 'Visitador',         corto: 'VS',
    border: 'border-cyan-200',    badge: 'bg-cyan-500',    texto: 'text-cyan-600',    bgActivo: 'bg-cyan-50/70',    ring: 'ring-cyan-300' },
]

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
    }`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

function Campo({ label, name, value, onChange, type = 'text', required = false, placeholder = '' }) {
  const id = useId()
  return (
    <div>
      <label htmlFor={id} className="block text-xs font-medium text-gray-600 mb-1">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <input
        id={id} name={name} type={type} value={value} onChange={onChange}
        required={required} placeholder={placeholder}
        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
      />
    </div>
  )
}

function OjoIcon({ visible }) {
  return visible ? (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
    </svg>
  ) : (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
    </svg>
  )
}

const ICONO_SALA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 6.75h1.5m-1.5 3h1.5m-1.5 3h1.5m3-6H15m-1.5 3H15m-1.5 3H15M9 21v-3.375c0-.621.504-1.125 1.125-1.125h3.75c.621 0 1.125.504 1.125 1.125V21" />
  </svg>
)

const ICONO_MAPA = (
  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
  </svg>
)

const ICONO_MAS = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
  </svg>
)

/* ─── Selector de varias ciudades REALES del catálogo — usado tanto para las salas del
   POLIGRAFISTA (con número de sala) como para las zonas del VISITADOR (solo ciudad).
   Ambos roles quedan validados de la misma forma: al menos una ciudad agregada.
   `value` es un array de { idCiudad, nombreCiudad, numeroSala } — no texto libre — para que a
   futuro se pueda filtrar por idCiudad quién puede atender un servicio en esa ciudad. ─── */
function SelectorMultiCiudad({ value, onChange, conSala = false, tieneError }) {
  const entradas = value ?? []
  const [ciudades, setCiudades] = useState([])
  const [agregando, setAgregando] = useState(false)
  const [idCiudadNueva, setIdCiudadNueva] = useState(null)
  const [salaNueva, setSalaNueva] = useState('1')

  useEffect(() => {
    catalogoService.listarCiudades().then(setCiudades).catch(() => {})
  }, [])

  const agregar = () => {
    if (!idCiudadNueva) return
    const numeroSala = conSala ? Number(salaNueva || 1) : null
    const yaExiste = entradas.some(e => e.idCiudad === idCiudadNueva && (!conSala || e.numeroSala === numeroSala))
    if (!yaExiste) {
      const ciudad = ciudades.find(c => c.idCiudad === idCiudadNueva)
      onChange([...entradas, { idCiudad: idCiudadNueva, nombreCiudad: ciudad?.nombreCiudad ?? '', numeroSala }])
    }
    setIdCiudadNueva(null)
    setSalaNueva('1')
    setAgregando(false)
  }

  const quitar = (idx) => onChange(entradas.filter((_, i) => i !== idx))

  return (
    <div className={`border rounded-lg p-2.5 space-y-2 transition-colors ${
      tieneError ? 'border-red-300 bg-red-50/40' : 'border-gray-200 bg-white'
    }`}>
      {entradas.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {entradas.map((e, idx) => (
            <span key={`${e.idCiudad}-${e.numeroSala}-${idx}`} className="inline-flex items-center gap-1.5 bg-primary-50 text-primary-700 text-xs font-medium pl-2 pr-1.5 py-1 rounded-full">
              <span className="text-primary-500">{conSala ? ICONO_SALA : ICONO_MAPA}</span>
              {e.nombreCiudad}{conSala && e.numeroSala ? ` — Sala ${e.numeroSala}` : ''}
              <button type="button" onClick={() => quitar(idx)} className="text-primary-400 hover:text-primary-700 leading-none text-sm">×</button>
            </span>
          ))}
        </div>
      )}

      {agregando ? (
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <CiudadSelect value={idCiudadNueva} onChange={setIdCiudadNueva} placeholder="Busca la ciudad…" />
          </div>
          {conSala && (
            <input aria-label="Número de sala" type="number" min={1} value={salaNueva} onChange={e => setSalaNueva(e.target.value)}
              placeholder="N.° sala"
              className="w-20 border border-gray-200 rounded-lg px-2 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          )}
          <button type="button" onClick={agregar} disabled={!idCiudadNueva}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-xs font-medium px-3 py-2 rounded-lg whitespace-nowrap transition-colors">
            Agregar
          </button>
          <button type="button" onClick={() => { setAgregando(false); setIdCiudadNueva(null) }}
            className="text-xs text-gray-400 hover:text-gray-600 px-1">
            Cancelar
          </button>
        </div>
      ) : (
        <button type="button" onClick={() => setAgregando(true)}
          className="w-full flex items-center justify-center gap-1.5 border border-dashed border-gray-300 rounded-lg px-3 py-2 text-xs text-gray-500 hover:border-primary-300 hover:text-primary-600 hover:bg-primary-50/50 transition-colors">
          {ICONO_MAS} {conSala ? 'Agregar ciudad y sala' : 'Agregar ciudad'}
        </button>
      )}
    </div>
  )
}

/* ─── Icono de orden ─── */
function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-primary-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

/* ─── Iconos de acciones de tabla ─── */
function IconoOjo() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
    </svg>
  )
}
function IconoLapiz() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
    </svg>
  )
}
function IconoPower() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5.636 5.636a9 9 0 1012.728 0M12 3v9" />
    </svg>
  )
}
function IconoLlave({ className = 'h-3.5 w-3.5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
    </svg>
  )
}

/* ─── Paginación ─── */
/* ─── Helpers de fecha ─── */
function fmtFecha(iso) {
  if (!iso) return '—'
  return parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}
function fmtFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/* ─── Modal detalle empleado ─── */
function ModalDetalleEmpleado({ usuario, onClose }) {
  const inicial = (usuario.nombre?.[0] ?? '?').toUpperCase()

  return (
    <Modal titulo={`${usuario.nombre} ${usuario.apellido ?? ''}`} subtitulo={usuario.email} onClose={onClose} ancho="max-w-md">
        {/* ── Avatar y estado ── */}
        <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-4 flex-shrink-0">
          <div className="w-14 h-14 rounded-full bg-primary-100 text-primary-700 text-2xl font-bold flex items-center justify-center flex-shrink-0">
            {inicial}
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
              usuario.activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
            }`}>
              {usuario.activo ? 'Activo' : 'Inactivo'}
            </span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-primary-50 text-primary-700 font-medium">
              {ETIQUETA_ROL[usuario.rol] ?? usuario.rol}
            </span>
          </div>
        </div>

          {/* Datos del empleado */}
          <Seccion titulo="Datos del empleado">
            <FilaDetalle label="ID Empleado" valor={usuario.idEmpleado ?? '—'} />
            <FilaDetalle label="Tipo de documento" valor={usuario.tipoDocumento ?? '—'} />
            <FilaDetalle label="N.° de documento" valor={usuario.documento ?? '—'} />
            <FilaDetalle label="Teléfono" valor={usuario.telefono ?? '—'} />
            <FilaDetalle label="Ciudad de residencia" valor={usuario.nombreCiudad ?? '—'} />
            <FilaDetalle label="Fecha de ingreso" valor={fmtFecha(usuario.fechaIngreso)} />
          </Seccion>

          {/* Cuenta de acceso */}
          <Seccion titulo="Cuenta de acceso">
            <FilaDetalle label="ID Usuario" valor={usuario.idUsuario} />
            <FilaDetalle label="Email" valor={usuario.email} truncar={false} />
            <FilaDetalle label="Email verificado"
              valor={
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  usuario.emailVerificado ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
                }`}>
                  {usuario.emailVerificado ? 'Verificado' : 'Sin verificar'}
                </span>
              }
            />
            <FilaDetalle label="Requiere 2FA"
              valor={
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  usuario.requiere2fa ? 'bg-primary-100 text-primary-700' : 'bg-gray-100 text-gray-500'
                }`}>
                  {usuario.requiere2fa ? 'Sí' : 'No'}
                </span>
              }
            />
            <FilaDetalle label="Cuenta creada" valor={fmtFecha(usuario.fechaCreacion)} />
            <FilaDetalle label="Último acceso" valor={fmtFechaHora(usuario.ultimoAcceso)} />
          </Seccion>

          {/* Estado de contraseña */}
          <Seccion titulo="Contraseña">
            <FilaDetalle label="Cambio requerido"
              valor={
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  usuario.requiereCambioPassword ? 'bg-amber-100 text-amber-700' : 'bg-green-100 text-green-700'
                }`}>
                  {usuario.requiereCambioPassword ? 'Pendiente' : 'Al día'}
                </span>
              }
            />
            <FilaDetalle label="Último cambio" valor={fmtFechaHora(usuario.fechaUltimoCambioPassword)} />
          </Seccion>

          {/* Ciudades asignadas — salas (poligrafista) o zonas de cobertura (visitador) */}
          {(usuario.rol === 'POLIGRAFISTA' || usuario.rol === 'VISITADOR') && (
            <Seccion titulo={usuario.rol === 'POLIGRAFISTA' ? 'Salas asignadas' : 'Zonas de visita'}>
              {usuario.ciudadesAsignadas?.length > 0 ? (
                <div className="flex flex-wrap gap-2 pt-1">
                  {usuario.ciudadesAsignadas.map(c => (
                    <span key={`${c.idCiudad}-${c.numeroSala ?? ''}`} className="text-xs bg-primary-50 text-primary-700 border border-primary-100 px-2.5 py-1 rounded-full">
                      {c.nombreCiudad}{c.numeroSala ? ` — Sala ${c.numeroSala}` : ''}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-gray-400 py-1">Sin ciudades asignadas</p>
              )}
              {usuario.rol === 'POLIGRAFISTA' && (
                usuario.novedadesSala ? (
                  <div className="mt-2">
                    <p className="text-xs text-gray-400 mb-1">Novedades</p>
                    <p className="text-sm text-gray-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 leading-relaxed whitespace-pre-wrap">
                      {usuario.novedadesSala}
                    </p>
                  </div>
                ) : (
                  <FilaDetalle label="Novedades" valor="Sin novedades registradas" />
                )
              )}
            </Seccion>
          )}

        {/* Footer */}
        <div className="px-5 py-4 border-t border-gray-100 flex-shrink-0">
          <button onClick={onClose}
            className="w-full py-2.5 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-xl hover:bg-gray-50/70 transition-colors">
            Cerrar
          </button>
        </div>
    </Modal>
  )
}

function Seccion({ titulo, children }) {
  return (
    <div className="px-5 py-4 border-b border-gray-100 last:border-0">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">{titulo}</p>
      <div className="space-y-2.5">{children}</div>
    </div>
  )
}

function FilaDetalle({ label, valor, truncar = true }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="text-xs text-gray-400 flex-shrink-0 pt-0.5">{label}</span>
      {typeof valor === 'string' || typeof valor === 'number' ? (
        <span className={`text-xs font-medium text-gray-800 text-right ${truncar ? 'truncate max-w-[65%]' : 'break-all'}`}>
          {valor}
        </span>
      ) : (
        <span className="flex justify-end">{valor}</span>
      )}
    </div>
  )
}

/* ─── TH helper con sort ─── */
function Th({ children, sortable, onClick, ordenCol, col, ordenDir, cls = '' }) {
  return (
    <th
      onClick={sortable ? onClick : undefined}
      className={`px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider select-none whitespace-nowrap ${
        sortable ? 'cursor-pointer hover:text-gray-700' : ''
      } ${cls}`}
    >
      {children}
      {sortable && (
        ordenCol === col
          ? <span className="ml-1 text-primary-500">{ordenDir === 'asc' ? '↑' : '↓'}</span>
          : <span className="ml-1 text-gray-300">↕</span>
      )}
    </th>
  )
}

/* ─── Tabla internos ─── */
function TablaInternos({ usuarios, cargando, onCambiarEstado, onEditar, onDetalle, onCambiarPassword, ordenCol, ordenDir, onOrdenar }) {
  if (cargando) return (
    <div className="flex items-center justify-center py-16">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
    </div>
  )
  if (usuarios.length === 0) return (
    <div className="text-center py-16 text-gray-400 text-sm">No hay usuarios que coincidan con el filtro.</div>
  )

  return (
    <div className="overflow-x-auto scrollbar-thin">
      <table className="w-full divide-y divide-gray-100 text-sm">
        <thead className="bg-gray-50">
          <tr>
            <Th sortable onClick={() => onOrdenar('nombre')} ordenCol={ordenCol} col="nombre" ordenDir={ordenDir}>Nombre</Th>
            <Th sortable onClick={() => onOrdenar('email')} ordenCol={ordenCol} col="email" ordenDir={ordenDir} cls="hidden sm:table-cell">Email</Th>
            <Th sortable onClick={() => onOrdenar('rol')} ordenCol={ordenCol} col="rol" ordenDir={ordenDir} cls="hidden md:table-cell">Rol</Th>
            <Th cls="hidden lg:table-cell">Teléfono</Th>
            <Th cls="hidden lg:table-cell">Ciudad</Th>
            <Th sortable onClick={() => onOrdenar('activo')} ordenCol={ordenCol} col="activo" ordenDir={ordenDir}>Estado</Th>
            <Th>···</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {usuarios.map(u => (
            <tr key={u.idUsuario} className="hover:bg-gray-50/70 transition-colors">
              <td className="px-3 py-3 font-medium text-gray-900">
                {u.nombre} {u.apellido ?? ''}
                <p className="sm:hidden text-xs font-normal text-gray-500 mt-0.5 break-all">{u.email}</p>
                <p className="md:hidden text-xs font-normal text-primary-500 mt-0.5">{ETIQUETA_ROL[u.rol] ?? u.rol}</p>
              </td>
              <td className="hidden sm:table-cell px-3 py-3 text-gray-600 break-all">{u.email}</td>
              <td className="hidden md:table-cell px-3 py-3 text-gray-600 whitespace-nowrap">{ETIQUETA_ROL[u.rol] ?? u.rol}</td>
              <td className="hidden lg:table-cell px-3 py-3 text-gray-500">{u.telefono ?? '—'}</td>
              <td className="hidden lg:table-cell px-3 py-3 text-gray-500 whitespace-nowrap">{u.nombreCiudad ?? '—'}</td>
              <td className="px-3 py-3"><Badge activo={u.activo} /></td>
              <td className="px-3 py-3 text-center">
                <MenuAcciones opciones={[
                  { label: 'Detalle', icono: <IconoOjo />, onClick: () => onDetalle(u) },
                  { label: 'Editar', icono: <IconoLapiz />, onClick: () => onEditar(u) },
                  { label: 'Cambiar contraseña', icono: <IconoLlave />, onClick: () => onCambiarPassword(u) },
                  {
                    label: u.activo ? 'Desactivar' : 'Activar',
                    icono: <IconoPower />,
                    destacado: u.activo ? 'text-red-600' : 'text-green-700',
                    onClick: () => onCambiarEstado(u.idUsuario, !u.activo),
                  },
                ]} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ─── Modal crear ─── */
function ModalCrear({ onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombre: '', apellido: '', email: '', password: '',
    rol: 'GESTOR', telefono: '',
    tipoDocumento: 'CC', documento: '', idCiudadResidencia: null,
    novedadesSala: '', ciudadesAsignadas: [],
    idsSubprocesos: [],
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleChange = (e) => {
    const { name, value } = e.target
    // Al cambiar de rol, las capacidades del rol anterior ya no aplican (p.ej. de ANALISTA_INTERNO a POLIGRAFISTA).
    setForm(prev => ({ ...prev, [name]: value, ...(name === 'rol' ? { idsSubprocesos: [] } : {}) }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    try {
      const payload = { ...form, ciudadesAsignadas: form.ciudadesAsignadas.map(c => ({ idCiudad: c.idCiudad, numeroSala: c.numeroSala })) }
      await api.post('/usuarios-internos', payload)
      onGuardado()
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? err.response?.data?.message ?? 'Error al crear el usuario.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Nuevo usuario interno" onClose={onClose} ancho="max-w-lg">
      <FormularioUsuario
        form={form}
        onChange={handleChange}
        onSubmit={handleSubmit}
        onCancel={onClose}
        guardando={guardando}
        error={error}
        esEdicion={false}
      />
    </Modal>
  )
}

/* ─── Modal editar ─── */
function ModalEditar({ usuario, onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombre:          usuario.nombre ?? '',
    apellido:        usuario.apellido ?? '',
    email:           usuario.email ?? '',
    password:        '',
    rol:             usuario.rol ?? 'GESTOR',
    telefono:        usuario.telefono ?? '',
    tipoDocumento:   usuario.tipoDocumento ?? 'CC',
    documento:       usuario.documento ?? '',
    idCiudadResidencia: usuario.idCiudadResidencia ?? null,
    novedadesSala:   usuario.novedadesSala ?? '',
    ciudadesAsignadas: usuario.ciudadesAsignadas?.map(c => ({ idCiudad: c.idCiudad, nombreCiudad: c.nombreCiudad, numeroSala: c.numeroSala })) ?? [],
    idsSubprocesos:  usuario.subprocesosAsignados?.map(p => p.idTipoProgreso) ?? [],
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleChange = (e) => {
    const { name, value } = e.target
    // Al cambiar de rol, las capacidades del rol anterior ya no aplican (p.ej. de ANALISTA_INTERNO a POLIGRAFISTA).
    setForm(prev => ({ ...prev, [name]: value, ...(name === 'rol' ? { idsSubprocesos: [] } : {}) }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    try {
      const payload = {
        ...form,
        password: form.password.trim() || null,
        ciudadesAsignadas: form.ciudadesAsignadas.map(c => ({ idCiudad: c.idCiudad, numeroSala: c.numeroSala })),
      }
      await api.put(`/usuarios-internos/${usuario.idUsuario}`, payload)
      onGuardado()
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? err.response?.data?.message ?? 'Error al actualizar el usuario.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Editar — ${usuario.nombre} ${usuario.apellido ?? ''}`} onClose={onClose} ancho="max-w-lg">
      <FormularioUsuario
        form={form}
        onChange={handleChange}
        onSubmit={handleSubmit}
        onCancel={onClose}
        guardando={guardando}
        error={error}
        esEdicion={true}
      />
    </Modal>
  )
}

/* ─── Formulario compartido (crear y editar) ─── */
function FormularioUsuario({ form, onChange, onSubmit, onCancel, guardando, error, esEdicion }) {
  const idCampos = useId()
  const [mostrarPwd, setMostrarPwd] = useState(false)
  const [mostrarConfirm, setMostrarConfirm] = useState(false)
  const [confirmPwd, setConfirmPwd] = useState('')
  const [localError, setLocalError] = useState(null)
  const [subprocesos, setSubprocesos] = useState([])

  const rolActual = form.rol

  useEffect(() => {
    api.get('/catalogo/tipos-progreso')
      .then(r => setSubprocesos(r.data.filter(p => p.activo)))
      .catch(() => {})
  }, [])

  const ROLES_CON_CAPACIDADES = ['ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR']
  const subprocesosDelRol = subprocesos.filter(p => p.rolResponsable === rolActual)

  const toggleSubproceso = (idTipoProgreso) => {
    const actual = form.idsSubprocesos ?? []
    const nuevo = actual.includes(idTipoProgreso)
      ? actual.filter(id => id !== idTipoProgreso)
      : [...actual, idTipoProgreso]
    onChange({ target: { name: 'idsSubprocesos', value: nuevo } })
  }

  const handleLocalSubmit = (e) => {
    e.preventDefault()
    setLocalError(null)

    const pwd = form.password ?? ''
    if (!esEdicion && !pwd && !form.documento?.trim()) {
      setLocalError('Ingresa el número de documento (se usará como contraseña inicial) o una contraseña')
      return
    }
    if (pwd && pwd !== confirmPwd) {
      setLocalError('Las contraseñas no coinciden')
      return
    }
    if (pwd && pwd.length < 8) {
      setLocalError('La contraseña debe tener al menos 8 caracteres')
      return
    }
    if (rolActual === 'VISITADOR' && (form.ciudadesAsignadas?.length ?? 0) === 0) {
      setLocalError('Las zonas de visita son obligatorias para el rol Visitador')
      return
    }
    if (rolActual === 'POLIGRAFISTA' && (form.ciudadesAsignadas?.length ?? 0) === 0) {
      setLocalError('Debes asignar al menos una ciudad y sala para el rol Poligrafista')
      return
    }

    onSubmit(e)
  }

  const errMsg = localError || error

  return (
    <form onSubmit={handleLocalSubmit} className="px-6 py-5 space-y-4">
      {errMsg && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errMsg}</div>
      )}

      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Datos de acceso</p>

      <div className="grid grid-cols-2 gap-4">
        <Campo label="Nombre" name="nombre" value={form.nombre} onChange={onChange} required />
        <Campo label="Apellido" name="apellido" value={form.apellido} onChange={onChange} />
      </div>

      <Campo label="Email" name="email" type="email" value={form.email} onChange={onChange} required />

      {/* Contraseña con toggle show/hide — solo al crear. Para un usuario existente, la
          contraseña se cambia aparte desde el botón "Cambiar contraseña" de la tabla, que exige
          confirmar con la clave del propio admin. */}
      {!esEdicion && (
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">
              Contraseña <span className="text-gray-400 font-normal text-[10px]">(vacío = usa N.° documento)</span>
            </label>
            <div className="relative">
              <input id={`${idCampos}-1`}
                name="password" type={mostrarPwd ? 'text' : 'password'}
                value={form.password} onChange={onChange}
                minLength={form.password ? 8 : undefined}
                placeholder="Mín. 8 caracteres"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 pr-9 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <button type="button" onClick={() => setMostrarPwd(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                <OjoIcon visible={mostrarPwd} />
              </button>
            </div>
          </div>

          <div>
            <label htmlFor={`${idCampos}-2`} className="block text-xs font-medium text-gray-600 mb-1">Confirmar</label>
            <div className="relative">
              <input id={`${idCampos}-2`}
                type={mostrarConfirm ? 'text' : 'password'}
                value={confirmPwd}
                onChange={e => setConfirmPwd(e.target.value)}
                placeholder="Repite la contraseña"
                className={`w-full border rounded-lg px-3 py-2 pr-9 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 ${
                  confirmPwd && confirmPwd !== form.password ? 'border-red-300 bg-red-50' : 'border-gray-200'
                }`}
              />
              <button type="button" onClick={() => setMostrarConfirm(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                <OjoIcon visible={mostrarConfirm} />
              </button>
            </div>
            {confirmPwd && confirmPwd !== form.password && (
              <p className="text-xs text-red-500 mt-0.5">No coinciden</p>
            )}
          </div>
        </div>
      )}

      {/* Rol */}
      <div>
        <label htmlFor={`${idCampos}-3`} className="block text-xs font-medium text-gray-600 mb-1">
          Rol <span className="text-red-500">*</span>
        </label>
        <select id={`${idCampos}-3`} name="rol" value={form.rol} onChange={onChange} required
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
          {ROLES_OPCIONES.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
        </select>
      </div>

      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider pt-2">Datos del empleado</p>

      {/* Tipo y número de documento */}
      <div className="grid grid-cols-3 gap-3">
        <div>
          <label htmlFor={`${idCampos}-4`} className="block text-xs font-medium text-gray-600 mb-1">Tipo doc.</label>
          <select id={`${idCampos}-4`} name="tipoDocumento" value={form.tipoDocumento} onChange={onChange}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            <option value="CC">C.C.</option>
            <option value="CE">C.E.</option>
            <option value="TI">T.I.</option>
            <option value="PP">Pasaporte</option>
            <option value="PEP">PEP</option>
          </select>
        </div>
        <div className="col-span-2">
          <Campo label="N.° de documento" name="documento" value={form.documento} onChange={onChange}
            placeholder="Ej: 1020304050" />
        </div>
      </div>

      {/* Teléfono y ciudad */}
      <div className="grid grid-cols-2 gap-3">
        <Campo label="Teléfono" name="telefono" value={form.telefono} onChange={onChange}
          placeholder="Ej: 3001234567" />
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad de residencia</label>
          <CiudadSelect
            value={form.idCiudadResidencia}
            onChange={(idCiudad) => onChange({ target: { name: 'idCiudadResidencia', value: idCiudad } })}
          />
        </div>
      </div>

      {/* Ciudades asignadas — POLIGRAFISTA (con sala) y VISITADOR (solo ciudad) comparten el
          mismo selector contra el catálogo real y la misma validación: al menos una ciudad. */}
      {(rolActual === 'POLIGRAFISTA' || rolActual === 'VISITADOR') && (
        <div>
          <label className="text-xs font-medium text-gray-600 mb-1 flex items-center gap-1">
            {rolActual === 'POLIGRAFISTA' ? ICONO_SALA : ICONO_MAPA}
            {rolActual === 'POLIGRAFISTA' ? 'Salas asignadas' : 'Zonas de visita'} <span className="text-red-500">*</span>
          </label>
          <SelectorMultiCiudad
            value={form.ciudadesAsignadas}
            onChange={(lista) => onChange({ target: { name: 'ciudadesAsignadas', value: lista } })}
            conSala={rolActual === 'POLIGRAFISTA'}
            tieneError={(form.ciudadesAsignadas?.length ?? 0) === 0}
          />
          <p className={`text-xs mt-1 ${(form.ciudadesAsignadas?.length ?? 0) === 0 ? 'text-red-500' : 'text-gray-400'}`}>
            {(form.ciudadesAsignadas?.length ?? 0) > 0
              ? (rolActual === 'POLIGRAFISTA' ? 'Ciudades y salas donde atiende este poligrafista.' : 'Ciudades de cobertura de este visitador.')
              : `Agrega al menos una ciudad${rolActual === 'POLIGRAFISTA' ? ' y sala' : ' de cobertura'} para este ${rolActual === 'POLIGRAFISTA' ? 'poligrafista' : 'visitador'}.`}
          </p>
          {rolActual === 'POLIGRAFISTA' && (
            <div className="mt-3">
              <label htmlFor={`${idCampos}-5`} className="block text-xs font-medium text-gray-600 mb-1">
                Novedades
                <span className="ml-1 text-gray-400 font-normal">(observaciones, incidencias, notas de sus salas)</span>
              </label>
              <textarea id={`${idCampos}-5`}
                value={form.novedadesSala ?? ''}
                onChange={e => onChange({ target: { name: 'novedadesSala', value: e.target.value } })}
                rows={3}
                placeholder="Registra aquí novedades, incidencias o notas importantes…"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
              />
            </div>
          )}
        </div>
      )}

      {/* Capacidades — qué subprocesos puede este empleado tener asignados. Solo se ofrecen los
          subprocesos cuyo rol_responsable coincide con el rol elegido (lo valida también el backend). */}
      {ROLES_CON_CAPACIDADES.includes(rolActual) && (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Subprocesos asignados</label>
          <p className="text-xs text-gray-400 mb-2">Subprocesos del catálogo que este {rolActual === 'ANALISTA_INTERNO' ? 'analista' : rolActual.toLowerCase()} puede ejecutar.</p>
          {subprocesosDelRol.length === 0 ? (
            <p className="text-xs text-gray-400">No hay subprocesos de este rol en el catálogo todavía.</p>
          ) : (
            <div className="border border-gray-200 rounded-lg divide-y divide-gray-100 max-h-48 overflow-y-auto">
              {subprocesosDelRol.map(p => (
                <label key={p.idTipoProgreso} className="flex items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 cursor-pointer">
                  <input type="checkbox"
                    checked={(form.idsSubprocesos ?? []).includes(p.idTipoProgreso)}
                    onChange={() => toggleSubproceso(p.idTipoProgreso)}
                    className="accent-primary-600" />
                  {p.nombreProgreso}
                </label>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="flex justify-end gap-3 pt-2">
        <button type="button" onClick={onCancel}
          className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 transition-colors">
          Cancelar
        </button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
          {guardando ? 'Guardando...' : esEdicion ? 'Guardar cambios' : 'Crear usuario'}
        </button>
      </div>
    </form>
  )
}

/* ─── Modal cambiar contraseña — aparte de Editar a propósito: no debería depender de que las
   ciudades/sala (poligrafista, visitador) o subprocesos también estén completos. ─── */
function ModalCambiarPassword({ usuario, onClose, onGuardado }) {
  const idCampos = useId()
  const [password, setPassword] = useState('')
  const [confirmPwd, setConfirmPwd] = useState('')
  const [claveAdmin, setClaveAdmin] = useState('')
  const [mostrarPwd, setMostrarPwd] = useState(false)
  const [mostrarClaveAdmin, setMostrarClaveAdmin] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const noCoinciden = confirmPwd !== '' && confirmPwd !== password
  const esFuerte = password.length >= 8

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    if (password.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres')
      return
    }
    if (password !== confirmPwd) {
      setError('Las contraseñas no coinciden')
      return
    }
    if (!claveAdmin) {
      setError('Confirma con tu propia contraseña para continuar')
      return
    }
    setGuardando(true)
    try {
      await api.patch(`/usuarios-internos/${usuario.idUsuario}/password`, { password, claveAdmin })
      onGuardado()
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo cambiar la contraseña.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Cambiar contraseña" subtitulo={`${usuario.nombre} ${usuario.apellido ?? ''}`} onClose={onClose} ancho="max-w-sm">
      <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
        <div className="flex items-center gap-3 pb-1">
          <div className="w-11 h-11 rounded-full bg-amber-100 flex items-center justify-center flex-shrink-0">
            <IconoLlave className="h-5 w-5 text-amber-600" />
          </div>
          <p className="text-xs text-gray-500">
            El usuario deberá volver a cambiarla la próxima vez que inicie sesión.
          </p>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
        )}
        <div>
          <label htmlFor={`${idCampos}-1`} className="block text-sm font-medium text-gray-700 mb-1.5">
            Nueva contraseña <span className="text-red-500">*</span>
          </label>
          <div className="relative">
            <input id={`${idCampos}-1`}
              type={mostrarPwd ? 'text' : 'password'} value={password}
              onChange={e => setPassword(e.target.value)} required minLength={8}
              placeholder="Mínimo 8 caracteres" autoFocus
              className={`w-full border rounded-lg px-4 py-2.5 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 ${
                password && !esFuerte ? 'border-red-300 bg-red-50' : 'border-gray-300'}`}
            />
            <button type="button" onClick={() => setMostrarPwd(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
              <OjoIcon visible={mostrarPwd} />
            </button>
          </div>
          {password && !esFuerte && <p className="text-xs text-red-500 mt-1">Mínimo 8 caracteres</p>}
          {password && esFuerte && <p className="text-xs text-green-600 mt-1">Contraseña válida</p>}
        </div>
        <div>
          <label htmlFor={`${idCampos}-2`} className="block text-sm font-medium text-gray-700 mb-1.5">
            Confirmar contraseña <span className="text-red-500">*</span>
          </label>
          <input id={`${idCampos}-2`}
            type={mostrarPwd ? 'text' : 'password'} value={confirmPwd}
            onChange={e => setConfirmPwd(e.target.value)} required
            placeholder="Repite la nueva contraseña"
            className={`w-full border rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 ${
              noCoinciden ? 'border-red-300 bg-red-50' : 'border-gray-300'}`}
          />
          {noCoinciden && <p className="text-xs text-red-500 mt-1">Las contraseñas no coinciden</p>}
        </div>
        <div className="pt-1 border-t border-gray-100">
          <label className="block text-sm font-medium text-gray-700 mb-1.5 mt-3">
            Tu contraseña <span className="text-red-500">*</span>
          </label>
          <p className="text-xs text-gray-400 mb-1.5">Confirma con tu propia clave para autorizar este cambio.</p>
          <div className="relative">
            <input aria-label="Tu contraseña actual"
              type={mostrarClaveAdmin ? 'text' : 'password'} value={claveAdmin}
              onChange={e => setClaveAdmin(e.target.value)} required
              placeholder="Tu contraseña actual"
              className="w-full border border-gray-300 rounded-lg px-4 py-2.5 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <button type="button" onClick={() => setMostrarClaveAdmin(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
              <OjoIcon visible={mostrarClaveAdmin} />
            </button>
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
            Cancelar
          </button>
          <button type="submit" disabled={guardando || noCoinciden || !esFuerte || !claveAdmin}
            className="px-4 py-2 text-sm font-medium bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white rounded-lg transition-colors">
            {guardando ? 'Guardando…' : 'Cambiar contraseña'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/* ─── Página principal ─── */
export default function UsuariosInternos() {
  const [internos, setInternos] = useState([])
  const [cargandoInternos, setCargandoInternos] = useState(true)
  const [modalCrear, setModalCrear] = useState(false)
  const [usuarioEditar, setUsuarioEditar] = useState(null)
  const [usuarioPassword, setUsuarioPassword] = useState(null)
  const [detalleUsuario, setDetalleUsuario] = useState(null)
  const [errorGlobal, setErrorGlobal] = useState(null)
  const [toast, setToast] = useState(null)

  // Prefiere el resultado del buscador global (Ctrl+K) — llega como /admin/usuarios-internos?q=nombre.
  const [searchParams] = useSearchParams()
  const [busqueda, setBusqueda] = useState(searchParams.get('q') ?? '')
  const [filtroRol, setFiltroRol] = useState('')
  const [ordenCol, setOrdenCol] = useState(null)
  const [ordenDir, setOrdenDir] = useState('asc')
  const [pagina, setPagina] = useState(1)
  const POR_PAGINA = 10

  const mostrarToast = useCallback((msg, tipo = 'exito') => setToast({ mensaje: msg, tipo }), [])

  const cargarInternos = async () => {
    setCargandoInternos(true)
    setErrorGlobal(null)
    try {
      const res = await api.get('/usuarios-internos')
      setInternos(res.data)
    } catch {
      setErrorGlobal('No se pudieron cargar los usuarios internos.')
    } finally {
      setCargandoInternos(false)
    }
  }

  useEffect(() => { cargarInternos() }, [])
  useEffect(() => { setPagina(1) }, [busqueda, filtroRol])

  const alternarRol = (rol) => setFiltroRol(prev => prev === rol ? '' : rol)

  const cambiarEstado = async (id, activar) => {
    try {
      await api.patch(`/usuarios-internos/${id}/${activar ? 'activar' : 'desactivar'}`)
      cargarInternos()
      mostrarToast(activar ? 'Usuario activado correctamente' : 'Usuario desactivado correctamente')
    } catch {
      setErrorGlobal('Error al cambiar el estado del usuario.')
    }
  }

  const manejarOrden = (col) => {
    if (ordenCol === col) setOrdenDir(prev => prev === 'asc' ? 'desc' : 'asc')
    else { setOrdenCol(col); setOrdenDir('asc') }
  }

  const internosFiltrados = useMemo(() => {
    let lista = internos
    if (filtroRol) lista = lista.filter(u => u.rol === filtroRol)
    // Varias palabras (todas deben aparecer, en cualquier campo) e insensible a tildes — así
    // "jose gestor" encuentra a "José Pérez" con rol Gestor sin que sea una frase exacta, y
    // buscar "poligrafista" encuentra por rol igual que por nombre/email/documento/ciudad.
    const terminos = normalizarTexto(busqueda).split(/\s+/).filter(Boolean)
    if (terminos.length > 0) {
      lista = lista.filter(u => {
        const texto = normalizarTexto([
          u.nombre, u.apellido, u.email, u.telefono, u.documento,
          u.nombreCiudad, ETIQUETA_ROL[u.rol] ?? u.rol,
        ].filter(Boolean).join(' '))
        return terminos.every(t => texto.includes(t))
      })
    }
    if (ordenCol) {
      lista = [...lista].sort((a, b) => {
        let va = ordenCol === 'activo' ? (a.activo ? 1 : 0) : (a[ordenCol] ?? '')
        let vb = ordenCol === 'activo' ? (b.activo ? 1 : 0) : (b[ordenCol] ?? '')
        if (typeof va === 'string') va = va.toLowerCase()
        if (typeof vb === 'string') vb = vb.toLowerCase()
        if (va < vb) return ordenDir === 'asc' ? -1 : 1
        if (va > vb) return ordenDir === 'asc' ? 1 : -1
        return 0
      })
    }
    return lista
  }, [internos, filtroRol, busqueda, ordenCol, ordenDir])

  const conteoPorRol = useMemo(() => {
    const mapa = {}
    internos.forEach(u => { mapa[u.rol] = (mapa[u.rol] ?? 0) + 1 })
    return mapa
  }, [internos])

  const totalPaginas    = Math.ceil(internosFiltrados.length / POR_PAGINA)
  const internosVisibles = internosFiltrados.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Equipo Polygraph</h2>
          <p className="text-sm text-gray-500 mt-0.5">Usuarios internos y gestión de accesos</p>
        </div>
        <button
          onClick={() => setModalCrear(true)}
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors"
        >
          <span className="text-base leading-none">+</span>
          Nuevo usuario interno
        </button>
      </div>

      {errorGlobal && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">
          {errorGlobal}
        </div>
      )}

      {/* Tarjetas de filtro por rol — clic en la activa vuelve a "Todos" (no hay tarjeta aparte) */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-4">
        {ROLES_FILTRO.map(r => {
          const activo = filtroRol === r.value
          return (
            <button key={r.value} type="button" onClick={() => alternarRol(r.value)}
              className={`text-left bg-white rounded-xl border shadow-sm p-3.5 cursor-pointer hover:shadow-md transition-shadow ${r.border} ${
                activo ? `${r.bgActivo} ring-2 ${r.ring}` : ''
              }`}>
              <div className="flex items-center gap-2 mb-2.5">
                <span className={`w-7 h-7 rounded-full ${r.badge} text-white text-[10px] font-bold flex items-center justify-center flex-shrink-0`}>
                  {r.corto}
                </span>
                <span className="text-xs font-medium text-gray-600 leading-tight">{r.label}</span>
              </div>
              <p className={`text-2xl font-bold ${r.texto}`}>{conteoPorRol[r.value] ?? 0}</p>
            </button>
          )
        })}
      </div>

      {/* Buscador */}
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input aria-label="Buscar por nombre, email, documento, teléfono, ciudad o rol…"
          type="search"
          value={busqueda}
          onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, email, documento, teléfono, ciudad o rol…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
        <span className="text-xs text-gray-400">
          {internosFiltrados.length} resultado{internosFiltrados.length !== 1 ? 's' : ''}
          {filtroRol && (
            <>
              {' · filtrando por '}
              <span className="font-medium text-gray-600">{ETIQUETA_ROL[filtroRol] ?? filtroRol}</span>
              {' — '}
              <button onClick={() => setFiltroRol('')} className="text-primary-600 hover:underline">ver todos</button>
            </>
          )}
        </span>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <TablaInternos
          usuarios={internosVisibles}
          cargando={cargandoInternos}
          onCambiarEstado={cambiarEstado}
          onEditar={setUsuarioEditar}
          onCambiarPassword={setUsuarioPassword}
          onDetalle={setDetalleUsuario}
          ordenCol={ordenCol}
          ordenDir={ordenDir}
          onOrdenar={manejarOrden}
        />
        <Paginacion
          pagina={pagina}
          total={internosFiltrados.length}
          porPagina={POR_PAGINA}
          onChange={setPagina}
        />
      </div>

      {modalCrear && (
        <ModalCrear onClose={() => setModalCrear(false)} onGuardado={() => { cargarInternos(); mostrarToast('Usuario creado correctamente') }} />
      )}

      {usuarioEditar && (
        <ModalEditar
          usuario={usuarioEditar}
          onClose={() => setUsuarioEditar(null)}
          onGuardado={() => { cargarInternos(); mostrarToast('Usuario actualizado correctamente') }}
        />
      )}

      {usuarioPassword && (
        <ModalCambiarPassword
          usuario={usuarioPassword}
          onClose={() => setUsuarioPassword(null)}
          onGuardado={() => mostrarToast('Contraseña cambiada correctamente')}
        />
      )}

      {detalleUsuario && (
        <ModalDetalleEmpleado usuario={detalleUsuario} onClose={() => setDetalleUsuario(null)} />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
