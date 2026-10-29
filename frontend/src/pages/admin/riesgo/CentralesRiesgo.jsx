import { useState, useEffect, useCallback } from 'react'
import api from '../../../services/api'
import Toast from '../../../components/Toast'
import {
  EncabezadoPagina, Etiqueta, TarjetaSeccion, AlertaError, EsqueletoPagina, BarraCambios,
} from '../../../components/admin/PaginaAdmin'

// Centrales de riesgo del Análisis financiero: el indicador de pago (score) se ubica en una de 4 bandas. El nombre de la
// central es SOLO interno (nunca se imprime en el informe); si cambia el proveedor, se ajustan sus bandas aquí.

const inputNumero = 'w-24 text-right tabular-nums border border-gray-300 rounded-lg px-2.5 py-1.5 text-sm font-medium text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500'
const inputTexto = 'border border-gray-300 rounded-lg px-2.5 py-1.5 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500'
const ACENTOS = ['bg-red-400', 'bg-orange-400', 'bg-amber-400', 'bg-emerald-500']
const ICONO_CENTRAL = <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5" /></svg>
const ICONO_BANDAS = <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" /></svg>

const BANDAS_NUEVA = [
  { desde: '150', hasta: '400', nombre: 'Muy bajo' }, { desde: '401', hasta: '500', nombre: 'Bajo' },
  { desde: '501', hasta: '600', nombre: 'Medio' }, { desde: '601', hasta: '950', nombre: 'Alto' },
]

const aEditable = (c) => ({
  idCentral: c.idCentral, nombre: c.nombre, activo: c.activo,
  bandas: c.bandas.map(b => ({ desde: String(b.desde), hasta: String(b.hasta), nombre: b.nombre })),
})

// Mismas reglas que el backend (CentralRiesgoService.aplicarBandas), para avisar antes de guardar.
function errorDe(c) {
  if (!c.nombre.trim()) return 'Escribe el nombre de la central (solo es para uso interno).'
  for (let i = 0; i < c.bandas.length; i++) {
    const b = c.bandas[i]
    const desde = Number(b.desde), hasta = Number(b.hasta)
    if (b.desde === '' || b.hasta === '' || !Number.isInteger(desde) || !Number.isInteger(hasta)) return `Banda ${i + 1}: completa "desde" y "hasta" con números enteros.`
    if (!b.nombre.trim()) return `Banda ${i + 1}: escribe su nombre.`
    if (desde > hasta) return `Banda ${i + 1}: "desde" no puede ser mayor que "hasta".`
    if (i > 0 && desde <= Number(c.bandas[i - 1].hasta)) return `Banda ${i + 1}: debe empezar después de donde termina la anterior.`
  }
  return null
}

export default function CentralesRiesgo() {
  const [original, setOriginal] = useState([])
  const [centrales, setCentrales] = useState([])
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [sucio, setSucio] = useState(false)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      const { data } = await api.get('/catalogo/riesgo/centrales')
      setOriginal(data)
      setCentrales(data.map(aEditable))
      setSucio(false)
    } catch { setError('No se pudieron cargar las centrales de riesgo.') }
    finally { setCargando(false) }
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const cambiar = (i, parche) => { setCentrales(prev => prev.map((c, k) => k === i ? { ...c, ...parche } : c)); setSucio(true) }
  const cambiarBanda = (i, j, campo, valor) => {
    setCentrales(prev => prev.map((c, k) => k !== i ? c : {
      ...c, bandas: c.bandas.map((b, m) => m === j ? { ...b, [campo]: campo === 'nombre' ? valor : valor.replace(/[^0-9]/g, '') } : b),
    }))
    setSucio(true)
  }
  const agregar = () => {
    setCentrales(prev => [...prev, { idCentral: null, nombre: '', activo: prev.length === 0, bandas: BANDAS_NUEVA.map(b => ({ ...b })) }])
    setSucio(true)
  }
  // La central que califica es la primera activa: activar una desactiva las demás para que no haya dudas.
  const activar = (i) => { setCentrales(prev => prev.map((c, k) => ({ ...c, activo: k === i }))); setSucio(true) }

  const hayErrores = centrales.some(errorDe) || !centrales.some(c => c.activo)

  const guardar = async () => {
    setGuardando(true); setError(null)
    try {
      for (const c of centrales) {
        const cuerpo = { nombre: c.nombre.trim(), activo: c.activo, bandas: c.bandas.map(b => ({ desde: Number(b.desde), hasta: Number(b.hasta), nombre: b.nombre.trim() })) }
        if (c.idCentral) await api.put(`/catalogo/riesgo/centrales/${c.idCentral}`, cuerpo)
        else await api.post('/catalogo/riesgo/centrales', cuerpo)
      }
      await cargar()
      setToast({ mensaje: 'Centrales de riesgo guardadas', tipo: 'exito' })
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudieron guardar las centrales de riesgo.')
    } finally { setGuardando(false) }
  }

  return (
    <div className="max-w-5xl">
      <EncabezadoPagina
        seccion="Calificación de riesgo"
        titulo="Centrales de riesgo"
        icono={ICONO_CENTRAL}
        descripcion="Bandas del indicador de pago (score) de cada central. El análisis financiero ubica el score del evaluado en la banda de la central activa. El nombre de la central es solo interno: nunca se imprime en el informe."
        meta={<Etiqueta tono="primario">{original.filter(c => c.activo).length} activa</Etiqueta>}
      />

      <AlertaError mensaje={error} onCerrar={() => setError(null)} />

      {cargando ? (
        <EsqueletoPagina indicadores={0} bloques={1} />
      ) : (
        <div className="space-y-5">
          {centrales.map((c, i) => {
            const err = errorDe(c)
            return (
              <TarjetaSeccion key={c.idCentral ?? `nueva-${i}`} icono={ICONO_BANDAS} titulo={c.nombre.trim() || 'Central nueva'}
                descripcion={c.activo ? 'Es la central que se usa para calificar.' : 'No se usa para calificar mientras esté inactiva.'}>
                <div className="flex flex-wrap items-end gap-4 mb-4">
                  <label className="block">
                    <span className="block text-[11px] font-medium text-gray-500 mb-1">Nombre (solo interno)</span>
                    <input value={c.nombre} onChange={e => cambiar(i, { nombre: e.target.value })} maxLength={100} className={`${inputTexto} w-72`} />
                  </label>
                  <label className="flex items-center gap-2 text-sm text-gray-700 pb-1.5">
                    <input type="checkbox" checked={c.activo} onChange={() => activar(i)} />
                    Central activa
                  </label>
                </div>
                <div className="space-y-2">
                  {c.bandas.map((b, j) => (
                    <div key={j} className="relative flex flex-wrap items-center gap-3 pl-4">
                      <span className={`absolute left-0 top-1 bottom-1 w-1 rounded-full ${ACENTOS[j] ?? 'bg-gray-300'}`} />
                      <span className="w-14 text-xs font-medium text-gray-500">Banda {j + 1}</span>
                      <label className="flex items-center gap-1.5 text-xs text-gray-500">Desde
                        <input aria-label={`Banda ${j + 1}, desde`} value={b.desde} onChange={e => cambiarBanda(i, j, 'desde', e.target.value)} inputMode="numeric" className={inputNumero} />
                      </label>
                      <label className="flex items-center gap-1.5 text-xs text-gray-500">Hasta
                        <input aria-label={`Banda ${j + 1}, hasta`} value={b.hasta} onChange={e => cambiarBanda(i, j, 'hasta', e.target.value)} inputMode="numeric" className={inputNumero} />
                      </label>
                      <label className="flex items-center gap-1.5 text-xs text-gray-500">Se llama
                        <input aria-label={`Banda ${j + 1}, nombre`} value={b.nombre} onChange={e => cambiarBanda(i, j, 'nombre', e.target.value)} maxLength={50} className={`${inputTexto} w-36`} />
                      </label>
                      <span className="text-xs text-gray-400">En el informe: «{b.desde} a {b.hasta} {b.nombre}»</span>
                    </div>
                  ))}
                </div>
                {err && <p className="text-xs text-red-600 mt-3">{err}</p>}
              </TarjetaSeccion>
            )
          })}
          <button type="button" onClick={agregar}
            className="text-sm font-medium text-primary-700 hover:text-primary-800 border border-dashed border-primary-300 rounded-xl px-4 py-2.5 w-full">
            + Agregar otra central
          </button>
          {!sucio && <p className="text-center text-xs text-gray-400">El cambio rige de inmediato en los estudios que se calculen desde ahora.</p>}
        </div>
      )}

      <BarraCambios
        visible={sucio}
        mensaje={hayErrores ? 'Hay valores por corregir antes de guardar (y debe haber una central activa)' : 'Cambios sin guardar'}
        onDescartar={() => cargar()}
        onGuardar={guardar}
        guardando={guardando}
        deshabilitado={hayErrores}
        textoGuardar="Guardar centrales" />

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
