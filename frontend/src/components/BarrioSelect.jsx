import { useId, useEffect, useMemo, useState } from 'react'
import catalogoService from '../services/catalogoService'
import { Modal } from './ui/Modal'
import CiudadSelect from './CiudadSelect'

const defaultCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none bg-white disabled:opacity-60'

const quitarTildes = (s) => (s || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/**
 * Combobox con búsqueda sobre el catálogo de barrios (/api/v1/barrios?idCiudad=).
 *
 * Dos modos:
 * - Acoplado a una ciudad (pasando `idCiudad`) — caso normal: candidato/cliente, mismo patrón que
 *   CiudadSelect.jsx. Al agregar un barrio nuevo, queda asociado a esa ciudad automáticamente.
 * - "Libre" (sin `idCiudad`) — usado por las plantillas de formulario dinámicas (ver
 *   TipoDatoFormulario.BARRIO), donde no hay necesariamente un campo de ciudad emparejado en la
 *   misma sección. Busca en TODO el catálogo (mostrando la ciudad de cada barrio para
 *   desambiguar) y, al agregar uno nuevo, pide elegir la ciudad ahí mismo en el modal.
 *
 * `valueType="id"` (default) trabaja con idBarrio; `valueType="nombre"` con el nombre como string
 * (para columnas de plantilla, que guardan todo como texto plano, igual que TEXTO/LISTA).
 */
function BarrioSelect({ idCiudad, value, onChange, className, required = false, valueType = 'id', id, name, placeholder = 'Selecciona o busca tu barrio...', disabled = false }) {
  const idCampos = useId()
  const modoLibre = !idCiudad
  const [barrios, setBarrios] = useState([])
  const [cargando, setCargando] = useState(!modoLibre)
  const [query, setQuery] = useState('')
  const [abierto, setAbierto] = useState(false)

  const [modalAbierto, setModalAbierto] = useState(false)
  const [mNombre, setMNombre] = useState('')
  const [mIdCiudad, setMIdCiudad] = useState(idCiudad ?? null)
  const [guardandoModal, setGuardandoModal] = useState(false)
  const [errorModal, setErrorModal] = useState(null)

  // Siempre entrega (valor, nombre) — `valor` es idBarrio o el nombre según `valueType`, `nombre`
  // es siempre el texto legible (útil para quien además quiera sincronizar un campo de texto
  // aparte, como EvalueeForm.jsx con `form.barrio`).
  const emitirCambio = (idBarrio, nombre) => onChange(valueType === 'nombre' ? (nombre || '') : idBarrio, nombre || '')

  // Modo libre: carga el catálogo completo una sola vez. Modo acoplado: no se limpia `value` de
  // forma incondicional al cambiar idCiudad (rompería la restauración de un formulario guardado,
  // la ciudad llega async después del mount) — solo se limpia si, tras cargar los barrios de la
  // ciudad actual, el valor seleccionado no pertenece a esa lista.
  useEffect(() => {
    if (modoLibre) {
      setCargando(true)
      catalogoService.listarBarrios(undefined)
        .then((data) => setBarrios(data))
        .catch(() => setBarrios([]))
        .finally(() => setCargando(false))
      return
    }
    if (!idCiudad) {
      setBarrios([])
      if (value) emitirCambio(null, '')
      return
    }
    let activo = true
    setCargando(true)
    catalogoService.listarBarrios(idCiudad)
      .then((data) => {
        if (!activo) return
        setBarrios(data)
        if (valueType === 'id' && value && !data.some((b) => b.idBarrio === value)) emitirCambio(null, '')
      })
      .catch(() => { if (activo) setBarrios([]) })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idCiudad, modoLibre])

  const opcionValor = (b) => (valueType === 'nombre' ? b.nombre : b.idBarrio)
  const seleccionado = barrios.find((b) => opcionValor(b) === value)
  const etiqueta = (b) => modoLibre ? `${b.nombre} — ${b.nombreCiudad}` : b.nombre
  // Mismo criterio que CiudadSelect: un valueType="nombre" sin match en el catálogo muestra el
  // texto guardado tal cual, no lo deja vacío.
  const textoSinMatch = !seleccionado && valueType === 'nombre' ? (value || '') : ''

  const LIMITE_SIN_BUSQUEDA = 6

  const { visibles: filtrados, buscando, totalCoincidencias } = useMemo(() => {
    const q = quitarTildes(query.trim())
    if (!q) return { visibles: barrios.slice(0, LIMITE_SIN_BUSQUEDA), buscando: false, totalCoincidencias: barrios.length }
    const coincidencias = barrios.filter((b) =>
      quitarTildes(b.nombre).includes(q) || (modoLibre && quitarTildes(b.nombreCiudad).includes(q)))
    return { visibles: coincidencias, buscando: true, totalCoincidencias: coincidencias.length }
  }, [barrios, query, modoLibre])

  const seleccionar = (b) => {
    emitirCambio(b.idBarrio, b.nombre)
    setQuery('')
    setAbierto(false)
  }

  const abrirModalAgregar = () => {
    setAbierto(false)
    setErrorModal(null)
    setMNombre(query.trim())
    setMIdCiudad(idCiudad ?? null)
    setModalAbierto(true)
  }

  const guardarNuevoBarrio = async () => {
    if (!mNombre.trim()) {
      setErrorModal('Indica el nombre del barrio.')
      return
    }
    if (modoLibre && !mIdCiudad) {
      setErrorModal('Indica a qué ciudad pertenece.')
      return
    }
    setGuardandoModal(true)
    setErrorModal(null)
    try {
      const creado = await catalogoService.crearBarrio({ nombre: mNombre.trim(), idCiudad: modoLibre ? mIdCiudad : idCiudad })
      setBarrios((prev) => (prev.some((b) => b.idBarrio === creado.idBarrio) ? prev : [...prev, creado].sort((a, b) => a.nombre.localeCompare(b.nombre))))
      emitirCambio(creado.idBarrio, creado.nombre)
      setQuery('')
      setModalAbierto(false)
    } catch (err) {
      setErrorModal(err.response?.data?.mensaje ?? 'No se pudo agregar el barrio. Intenta de nuevo.')
    } finally {
      setGuardandoModal(false)
    }
  }

  const deshabilitado = disabled || (!modoLibre && !idCiudad) || cargando
  const textoPlaceholder = (!modoLibre && !idCiudad) ? 'Selecciona primero la ciudad...' : (cargando ? 'Cargando barrios...' : placeholder)

  return (
    <div className="relative">
      <input
        id={id}
        name={name}
        required={required}
        autoComplete="off"
        disabled={deshabilitado}
        value={abierto ? query : (seleccionado ? etiqueta(seleccionado) : textoSinMatch)}
        placeholder={textoPlaceholder}
        onFocus={() => { setAbierto(true); setQuery('') }}
        onClick={() => setAbierto(true)}
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
          {buscando && filtrados.length === 0 && (
            <p className="px-3 py-2 text-xs text-gray-400">Sin resultados para "{query}".</p>
          )}
          {!buscando && barrios.length === 0 && (
            <p className="px-3 py-2 text-xs text-gray-400">{modoLibre ? 'Aún no hay barrios registrados.' : 'Aún no hay barrios registrados para esta ciudad.'}</p>
          )}
          {filtrados.map((b) => (
            <button
              type="button"
              key={b.idBarrio}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => seleccionar(b)}
              className={`w-full text-left px-3 py-1.5 text-sm hover:bg-primary-50 transition-colors ${opcionValor(b) === value ? 'bg-primary-50 text-primary-700 font-medium' : 'text-gray-700'}`}
            >
              {b.nombre} {modoLibre && <span className="text-gray-400">— {b.nombreCiudad}</span>}
            </button>
          ))}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={abrirModalAgregar}
            className="w-full text-left px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 font-medium border-t border-gray-100 mt-1 pt-2 transition-colors"
          >
            + No encuentro mi barrio...
          </button>
        </div>
      )}

      {modalAbierto && (
        <Modal titulo="Agregar barrio al catálogo" subtitulo={modoLibre ? undefined : 'Quedará asociado a la ciudad seleccionada'} onClose={() => setModalAbierto(false)} ancho="max-w-sm">
          <div className="px-6 py-5 space-y-4">
            {errorModal && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{errorModal}</div>
            )}
            <div>
              <label htmlFor={`${idCampos}-1`} className="block text-xs font-medium text-gray-600 mb-1">Barrio <span className="text-red-500">*</span></label>
              <input id={`${idCampos}-1`} value={mNombre} onChange={(e) => setMNombre(e.target.value)} autoFocus
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none" />
            </div>
            {modoLibre && (
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad <span className="text-red-500">*</span></label>
                <CiudadSelect value={mIdCiudad} onChange={setMIdCiudad} />
              </div>
            )}
          </div>
          <div className="px-6 pb-5 flex justify-end gap-3">
            <button type="button" onClick={() => setModalAbierto(false)} disabled={guardandoModal}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
              Cancelar
            </button>
            <button type="button" onClick={guardarNuevoBarrio} disabled={guardandoModal}
              className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50 transition-colors">
              {guardandoModal ? 'Guardando...' : 'Guardar barrio'}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}

export default BarrioSelect
