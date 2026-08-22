import { useEffect, useMemo, useState } from 'react'
import catalogoService from '../services/catalogoService'
import { Modal } from './ui/Modal'

const defaultCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none bg-white disabled:opacity-60'

const quitarTildes = (s) => (s || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/**
 * Combobox con búsqueda sobre el catálogo de ciudades (/api/v1/ciudades).
 * valueType="id" (default) trabaja con el id_ciudad numérico — usado en Candidatos/Clientes/Usuarios internos.
 * valueType="nombre" trabaja con el nombre de la ciudad como string — usado en registros que aún
 * persisten la ciudad como texto libre (educación/experiencia laboral del evaluado).
 *
 * Si el usuario no encuentra su ciudad, un modal le permite agregarla al catálogo
 * (POST /api/v1/ciudades, público) con los mismos campos de la tabla "ciudades"
 * (nombre, departamento y, opcionalmente, códigos DANE). Queda seleccionada automáticamente.
 */
function CiudadSelect({ value, onChange, className, required = false, valueType = 'id', id, name, placeholder = 'Selecciona o busca una ciudad...' }) {
  const [ciudades, setCiudades] = useState([])
  const [cargando, setCargando] = useState(true)
  const [query, setQuery] = useState('')
  const [abierto, setAbierto] = useState(false)

  const [modalAbierto, setModalAbierto] = useState(false)
  const [mNombre, setMNombre] = useState('')
  const [mDepartamento, setMDepartamento] = useState('')
  const [mCodigoCiudad, setMCodigoCiudad] = useState('')
  const [mCodigoDepto, setMCodigoDepto] = useState('')
  const [guardandoModal, setGuardandoModal] = useState(false)
  const [errorModal, setErrorModal] = useState(null)

  useEffect(() => {
    let activo = true
    catalogoService.listarCiudades()
      .then((data) => { if (activo) setCiudades(data) })
      .catch(() => { if (activo) setCiudades([]) })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [])

  const opcionValor = (c) => (valueType === 'nombre' ? c.nombreCiudad : c.idCiudad)
  const etiqueta = (c) => `${c.nombreCiudad} — ${c.departamento}`
  const seleccionada = ciudades.find((c) => opcionValor(c) === value)

  const LIMITE_SIN_BUSQUEDA = 6

  const { visibles: filtradas, buscando, totalCoincidencias } = useMemo(() => {
    const q = quitarTildes(query.trim())
    if (!q) return { visibles: ciudades.slice(0, LIMITE_SIN_BUSQUEDA), buscando: false, totalCoincidencias: ciudades.length }
    const coincidencias = ciudades.filter((c) => quitarTildes(c.nombreCiudad).includes(q) || quitarTildes(c.departamento).includes(q))
    return { visibles: coincidencias, buscando: true, totalCoincidencias: coincidencias.length }
  }, [ciudades, query])

  const seleccionar = (c) => {
    onChange(opcionValor(c))
    setQuery('')
    setAbierto(false)
  }

  const abrirModalAgregar = () => {
    setAbierto(false)
    setErrorModal(null)
    setMNombre(query.trim())
    setMDepartamento('')
    setMCodigoCiudad('')
    setMCodigoDepto('')
    setModalAbierto(true)
  }

  const guardarNuevaCiudad = async () => {
    if (!mNombre.trim() || !mDepartamento.trim()) {
      setErrorModal('Indica el nombre de la ciudad y el departamento.')
      return
    }
    setGuardandoModal(true)
    setErrorModal(null)
    try {
      const creada = await catalogoService.crearCiudad({
        nombreCiudad: mNombre.trim(),
        departamento: mDepartamento.trim(),
        codigoDaneCiudad: mCodigoCiudad.trim() || null,
        codigoDaneDepto: mCodigoDepto.trim() || null,
      })
      setCiudades((prev) => (prev.some((c) => c.idCiudad === creada.idCiudad) ? prev : [...prev, creada].sort((a, b) => a.nombreCiudad.localeCompare(b.nombreCiudad))))
      onChange(valueType === 'nombre' ? creada.nombreCiudad : creada.idCiudad)
      setQuery('')
      setModalAbierto(false)
    } catch {
      setErrorModal('No se pudo agregar la ciudad. Intenta de nuevo.')
    } finally {
      setGuardandoModal(false)
    }
  }

  return (
    <div className="relative">
      <input
        id={id}
        name={name}
        required={required}
        autoComplete="off"
        disabled={cargando}
        value={abierto ? query : (seleccionada ? etiqueta(seleccionada) : '')}
        placeholder={cargando ? 'Cargando ciudades...' : placeholder}
        onFocus={() => { setAbierto(true); setQuery('') }}
        onBlur={() => setAbierto(false)}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Escape') { setAbierto(false); e.currentTarget.blur() } }}
        className={className || defaultCls}
      />
      {abierto && (
        <div className="absolute z-20 mt-1 w-full max-h-56 overflow-y-auto bg-white border border-gray-200 rounded-lg shadow-lg py-1">
          {!buscando && totalCoincidencias > LIMITE_SIN_BUSQUEDA && (
            <p className="px-3 pt-1 pb-1.5 text-[11px] text-gray-400">
              Mostrando {LIMITE_SIN_BUSQUEDA} de {totalCoincidencias} — escribe para buscar el resto.
            </p>
          )}
          {buscando && filtradas.length === 0 && (
            <p className="px-3 py-2 text-xs text-gray-400">Sin resultados para "{query}".</p>
          )}
          {filtradas.map((c) => (
            <button
              type="button"
              key={c.idCiudad}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => seleccionar(c)}
              className={`w-full text-left px-3 py-1.5 text-sm hover:bg-primary-50 transition-colors ${opcionValor(c) === value ? 'bg-primary-50 text-primary-700 font-medium' : 'text-gray-700'}`}
            >
              {c.nombreCiudad} <span className="text-gray-400">— {c.departamento}</span>
            </button>
          ))}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={abrirModalAgregar}
            className="w-full text-left px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 font-medium border-t border-gray-100 mt-1 pt-2 transition-colors"
          >
            + No encuentro mi ciudad...
          </button>
        </div>
      )}

      {modalAbierto && (
        <Modal titulo="Agregar ciudad al catálogo" subtitulo="Se usará también para calcular tarifas por ciudad" onClose={() => setModalAbierto(false)} ancho="max-w-sm">
          <div className="px-6 py-5 space-y-4">
            {errorModal && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorModal}</div>
            )}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad <span className="text-red-500">*</span></label>
              <input value={mNombre} onChange={(e) => setMNombre(e.target.value)} autoFocus
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Departamento <span className="text-red-500">*</span></label>
              <input value={mDepartamento} onChange={(e) => setMDepartamento(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Código DANE ciudad</label>
                <input value={mCodigoCiudad} onChange={(e) => setMCodigoCiudad(e.target.value)} maxLength={10} placeholder="Opcional"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Código DANE depto.</label>
                <input value={mCodigoDepto} onChange={(e) => setMCodigoDepto(e.target.value)} maxLength={2} placeholder="Opcional"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
              </div>
            </div>
          </div>
          <div className="px-6 pb-5 flex justify-end gap-3">
            <button type="button" onClick={() => setModalAbierto(false)} disabled={guardandoModal}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
              Cancelar
            </button>
            <button type="button" onClick={guardarNuevaCiudad} disabled={guardandoModal}
              className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50 transition-colors">
              {guardandoModal ? 'Guardando...' : 'Guardar ciudad'}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}

export default CiudadSelect
