import { useState, useEffect, useCallback, useMemo } from 'react'
import api from '../../services/api'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'
import CiudadSelect from '../../components/CiudadSelect'

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
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <input
        name={name} type={type} value={value} onChange={onChange}
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

const CIUDADES_CO = [
  'Bogotá', 'Medellín', 'Cali', 'Barranquilla', 'Cartagena',
  'Cúcuta', 'Pereira', 'Manizales', 'Ibagué', 'Bucaramanga',
  'Santa Marta', 'Villavicencio', 'Neiva', 'Pasto', 'Armenia',
  'Valledupar', 'Montería', 'Sincelejo', 'Popayán', 'Tunja',
]

/* ─── Sub-modal selección de sala (para POLIGRAFISTA) ─── */
function ModalSala({ valorActual, novedadesActual, onConfirmar, onClose }) {
  const match = valorActual?.match(/^(.+) - Sala (\d+)$/)
  const [ciudad, setCiudad] = useState(match?.[1] ?? CIUDADES_CO[0])
  const [numero, setNumero] = useState(match?.[2] ?? '1')
  const [novedades, setNovedades] = useState(novedadesActual ?? '')

  return (
    <Modal titulo="Configurar sala" onClose={onClose} ancho="max-w-md">
      <div className="px-5 py-4 space-y-4">
          {/* Ciudad y número */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad <span className="text-red-500">*</span></label>
              <select value={ciudad} onChange={e => setCiudad(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
                {CIUDADES_CO.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">N.° de sala <span className="text-red-500">*</span></label>
              <input type="number" min={1} value={numero}
                onChange={e => setNumero(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>

          {/* Preview */}
          <div className="bg-primary-50 rounded-lg px-3 py-2 text-sm text-primary-700 font-medium text-center">
            {ciudad} — Sala {numero || '?'}
          </div>

          {/* Novedades */}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">
              Novedades de la sala
              <span className="ml-1 text-gray-400 font-normal">(observaciones, incidencias, notas)</span>
            </label>
            <textarea
              value={novedades}
              onChange={e => setNovedades(e.target.value)}
              rows={4}
              placeholder="Registra aquí novedades, incidencias o notas importantes de esta sala…"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
            />
            {novedades && (
              <p className="text-xs text-gray-400 mt-0.5 text-right">{novedades.length} caracteres</p>
            )}
          </div>
        </div>

        <div className="px-5 pb-5 flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button
            onClick={() => { if (numero && ciudad) { onConfirmar(`${ciudad} - Sala ${numero}`, novedades); onClose() } }}
            disabled={!numero || !ciudad}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors"
          >
            Confirmar
          </button>
        </div>
    </Modal>
  )
}

/* ─── Icono de orden ─── */
function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-primary-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

/* ─── Paginación ─── */
function Paginacion({ pagina, total, porPagina, onChange }) {
  const totalPags = Math.ceil(total / porPagina)
  if (totalPags <= 1) return null
  const inicio = (pagina - 1) * porPagina + 1
  const fin    = Math.min(pagina * porPagina, total)
  const nums = []
  for (let i = Math.max(1, pagina - 2); i <= Math.min(totalPags, pagina + 2); i++) nums.push(i)
  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
      <span className="text-xs text-gray-400">{inicio}–{fin} de {total}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(pagina - 1)} disabled={pagina === 1}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          ← Anterior
        </button>
        {nums[0] > 1 && <span className="text-xs text-gray-400 px-1">…</span>}
        {nums.map(p => (
          <button key={p} onClick={() => onChange(p)}
            className={`w-8 h-8 text-xs rounded-lg border transition-colors ${
              p === pagina ? 'bg-primary-600 border-primary-600 text-white font-semibold' : 'border-gray-200 text-gray-500 hover:bg-gray-50'
            }`}>
            {p}
          </button>
        ))}
        {nums[nums.length - 1] < totalPags && <span className="text-xs text-gray-400 px-1">…</span>}
        <button onClick={() => onChange(pagina + 1)} disabled={pagina === totalPags}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          Siguiente →
        </button>
      </div>
    </div>
  )
}

/* ─── Helpers de fecha ─── */
function fmtFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
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

          {/* Sala de trabajo */}
          <Seccion titulo="Sala de trabajo">
            <FilaDetalle label="Sala asignada" valor={usuario.salaEncargada || '—'} />
            {usuario.novedadesSala ? (
              <div className="mt-2">
                <p className="text-xs text-gray-400 mb-1">Novedades</p>
                <p className="text-sm text-gray-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 leading-relaxed whitespace-pre-wrap">
                  {usuario.novedadesSala}
                </p>
              </div>
            ) : (
              <FilaDetalle label="Novedades" valor="Sin novedades registradas" />
            )}
          </Seccion>

          {/* Zonas de visita */}
          <Seccion titulo="Zonas de visita">
            {usuario.zonasVisita ? (
              <div className="flex flex-wrap gap-2 pt-1">
                {usuario.zonasVisita.split(',').map(z => z.trim()).filter(Boolean).map(zona => (
                  <span key={zona} className="text-xs bg-primary-50 text-primary-700 border border-primary-100 px-2.5 py-1 rounded-full">
                    {zona}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-xs text-gray-400 py-1">Sin zonas asignadas</p>
            )}
          </Seccion>

        {/* Footer */}
        <div className="px-5 py-4 border-t border-gray-100 flex-shrink-0">
          <button onClick={onClose}
            className="w-full py-2.5 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors">
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
      className={`px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider select-none whitespace-nowrap ${
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
function TablaInternos({ usuarios, cargando, onCambiarEstado, onEditar, onDetalle, ordenCol, ordenDir, onOrdenar }) {
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
            <Th>Acciones</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {usuarios.map(u => (
            <tr key={u.idUsuario} className="hover:bg-gray-50 transition-colors">
              <td className="px-4 py-3 font-medium text-gray-900 whitespace-nowrap">
                {u.nombre} {u.apellido ?? ''}
                <p className="sm:hidden text-xs font-normal text-gray-500 mt-0.5">{u.email}</p>
                <p className="md:hidden text-xs font-normal text-primary-500 mt-0.5">{ETIQUETA_ROL[u.rol] ?? u.rol}</p>
              </td>
              <td className="hidden sm:table-cell px-4 py-3 text-gray-600 whitespace-nowrap">{u.email}</td>
              <td className="hidden md:table-cell px-4 py-3 text-gray-600 whitespace-nowrap">{ETIQUETA_ROL[u.rol] ?? u.rol}</td>
              <td className="hidden lg:table-cell px-4 py-3 text-gray-500">{u.telefono ?? '—'}</td>
              <td className="hidden lg:table-cell px-4 py-3 text-gray-500 whitespace-nowrap">{u.nombreCiudad ?? '—'}</td>
              <td className="px-4 py-3"><Badge activo={u.activo} /></td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2 flex-wrap">
                  <button onClick={() => onDetalle(u)}
                    className="text-xs font-medium px-3 py-1 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors whitespace-nowrap">
                    Detalle
                  </button>
                  <button onClick={() => onEditar(u)}
                    className="text-xs font-medium px-3 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors whitespace-nowrap">
                    Editar
                  </button>
                  <button onClick={() => onCambiarEstado(u.idUsuario, !u.activo)}
                    className={`text-xs font-medium px-3 py-1 rounded-full border transition-colors whitespace-nowrap ${
                      u.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'
                    }`}>
                    {u.activo ? 'Desactivar' : 'Activar'}
                  </button>
                </div>
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
    salaEncargada: '', novedadesSala: '', zonasVisita: '',
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
      await api.post('/usuarios-internos', form)
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
    salaEncargada:   usuario.salaEncargada ?? '',
    novedadesSala:   usuario.novedadesSala ?? '',
    zonasVisita:     usuario.zonasVisita ?? '',
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
      const payload = { ...form, password: form.password.trim() || null }
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
  const [mostrarPwd, setMostrarPwd] = useState(false)
  const [mostrarConfirm, setMostrarConfirm] = useState(false)
  const [confirmPwd, setConfirmPwd] = useState('')
  const [localError, setLocalError] = useState(null)
  const [salaModal, setSalaModal] = useState(false)
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
    if (rolActual === 'VISITADOR' && !form.zonasVisita?.trim()) {
      setLocalError('Las zonas de visita son obligatorias para el rol Visitador')
      return
    }
    if (rolActual === 'POLIGRAFISTA' && !form.salaEncargada?.trim()) {
      setLocalError('Debes configurar la sala encargada para el rol Poligrafista')
      return
    }

    onSubmit(e)
  }

  const setSala = (sala, novedadesSala) => {
    onChange({ target: { name: 'salaEncargada', value: sala } })
    onChange({ target: { name: 'novedadesSala', value: novedadesSala } })
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

      {/* Contraseña con toggle show/hide */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Contraseña
            {esEdicion
              ? <span className="text-gray-400 font-normal text-[10px]"> (vacío = sin cambios)</span>
              : <span className="text-gray-400 font-normal text-[10px]"> (vacío = usa N.° documento)</span>
            }
          </label>
          <div className="relative">
            <input
              name="password" type={mostrarPwd ? 'text' : 'password'}
              value={form.password} onChange={onChange}
              required={!esEdicion} minLength={form.password ? 8 : undefined}
              placeholder={esEdicion ? '••••••••' : 'Mín. 8 caracteres'}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 pr-9 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <button type="button" onClick={() => setMostrarPwd(v => !v)}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
              <OjoIcon visible={mostrarPwd} />
            </button>
          </div>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Confirmar {!esEdicion && <span className="text-red-500">*</span>}
          </label>
          <div className="relative">
            <input
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

      {/* Rol */}
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          Rol <span className="text-red-500">*</span>
        </label>
        <select name="rol" value={form.rol} onChange={onChange} required
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
          {ROLES_OPCIONES.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
        </select>
      </div>

      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider pt-2">Datos del empleado</p>

      {/* Tipo y número de documento */}
      <div className="grid grid-cols-3 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Tipo doc.</label>
          <select name="tipoDocumento" value={form.tipoDocumento} onChange={onChange}
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

      {/* Sala — solo POLIGRAFISTA usa el sub-modal */}
      {rolActual === 'POLIGRAFISTA' ? (
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Sala encargada <span className="text-red-500">*</span>
          </label>
          <div className="flex items-center gap-2">
            <div className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm bg-gray-50 text-gray-700 min-h-[37px]">
              {form.salaEncargada || <span className="text-gray-400">Sin sala configurada</span>}
            </div>
            <button type="button" onClick={() => setSalaModal(true)}
              className="bg-primary-600 hover:bg-primary-700 text-white text-xs font-medium px-3 py-2 rounded-lg whitespace-nowrap transition-colors">
              {form.salaEncargada ? 'Cambiar' : 'Configurar'}
            </button>
          </div>
        </div>
      ) : (
        <Campo label="Sala encargada" name="salaEncargada" value={form.salaEncargada}
          onChange={onChange} placeholder="Ej: Sala Bogotá Norte" />
      )}

      {/* Zonas — OBLIGATORIO para VISITADOR */}
      <Campo
        label="Zonas de visita"
        name="zonasVisita"
        value={form.zonasVisita}
        onChange={onChange}
        required={rolActual === 'VISITADOR'}
        placeholder={rolActual === 'VISITADOR' ? 'Obligatorio — Ej: Norte, Sur' : 'Ej: Norte, Sur, Occidente'}
      />

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

      {salaModal && (
        <ModalSala
          valorActual={form.salaEncargada}
          novedadesActual={form.novedadesSala}
          onConfirmar={setSala}
          onClose={() => setSalaModal(false)}
        />
      )}
    </form>
  )
}

/* ─── Página principal ─── */
export default function UsuariosInternos() {
  const [internos, setInternos] = useState([])
  const [cargandoInternos, setCargandoInternos] = useState(true)
  const [modalCrear, setModalCrear] = useState(false)
  const [usuarioEditar, setUsuarioEditar] = useState(null)
  const [detalleUsuario, setDetalleUsuario] = useState(null)
  const [errorGlobal, setErrorGlobal] = useState(null)
  const [toast, setToast] = useState(null)

  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [ordenCol, setOrdenCol] = useState(null)
  const [ordenDir, setOrdenDir] = useState('asc')
  const [pagina, setPagina] = useState(1)
  const POR_PAGINA = 7

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
  useEffect(() => { setPagina(1) }, [busqueda, filtroEstado])

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
    if (filtroEstado === 'activos')   lista = lista.filter(u => u.activo)
    if (filtroEstado === 'inactivos') lista = lista.filter(u => !u.activo)
    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(u =>
        `${u.nombre} ${u.apellido ?? ''}`.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        (ETIQUETA_ROL[u.rol] ?? u.rol).toLowerCase().includes(q)
      )
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
  }, [internos, filtroEstado, busqueda, ordenCol, ordenDir])

  const cuentaActivos   = internos.filter(u => u.activo).length
  const cuentaInactivos = internos.filter(u => !u.activo).length

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

      {/* Barra de filtros */}
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input
          type="search"
          value={busqueda}
          onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, email o rol…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
        <div className="flex gap-1">
          {[
            { key: 'todos',    label: 'Todos',    count: internos.length },
            { key: 'activos',  label: 'Activos',  count: cuentaActivos   },
            { key: 'inactivos',label: 'Inactivos',count: cuentaInactivos },
          ].map(f => (
            <button
              key={f.key}
              onClick={() => setFiltroEstado(f.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                filtroEstado === f.key
                  ? f.key === 'inactivos'
                    ? 'bg-red-100 text-red-700'
                    : f.key === 'activos'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-primary-100 text-primary-700'
                  : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}
            >
              {f.label}
              <span className="ml-1.5 font-normal opacity-70">{f.count}</span>
            </button>
          ))}
        </div>
        <span className="text-xs text-gray-400">
          {internosFiltrados.length} resultado{internosFiltrados.length !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <TablaInternos
          usuarios={internosVisibles}
          cargando={cargandoInternos}
          onCambiarEstado={cambiarEstado}
          onEditar={setUsuarioEditar}
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

      {detalleUsuario && (
        <ModalDetalleEmpleado usuario={detalleUsuario} onClose={() => setDetalleUsuario(null)} />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
