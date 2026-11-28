import { useEffect, useId, useState } from 'react'
import motivosService from '../services/motivosService'

const campoCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'

// Motivo de una acción que lo exige (devolver, cancelar, reprogramar): una lista que administra el admin y, si el motivo es
// «Otro», el texto de cuál. `valor` = { idMotivo, texto, requiereTexto }. Con `detalle`, el texto siempre se puede escribir
// (opcional salvo en «Otro»), para aclarar qué hay que corregir, por ejemplo.
export function motivoIncompleto(valor) {
  if (!valor?.idMotivo) return 'Elige el motivo.'
  if (valor.requiereTexto && !(valor.texto ?? '').trim()) return 'Escribe cuál es el motivo.'
  return null
}

export const MOTIVO_VACIO = { idMotivo: '', texto: '', requiereTexto: false }

export default function SelectorMotivo({ tipo, valor, onChange, etiqueta = 'Motivo', detalle = false, detalleEtiqueta = 'Detalle', autoFocus = false }) {
  const idCampo = useId()
  const [motivos, setMotivos] = useState(null)
  const [fallo, setFallo] = useState(false)

  useEffect(() => {
    let vigente = true
    motivosService.activos(tipo).then(m => { if (vigente) setMotivos(m) }).catch(() => { if (vigente) setFallo(true) })
    return () => { vigente = false }
  }, [tipo])

  const elegir = (e) => {
    const id = e.target.value
    const m = motivos?.find(x => String(x.id) === id)
    onChange({ idMotivo: id ? Number(id) : '', texto: valor.texto ?? '', requiereTexto: !!m?.requiereTexto })
  }

  const elegido = motivos?.find(m => m.id === valor.idMotivo)
  const pideTexto = !!elegido?.requiereTexto
  const verTexto = pideTexto || detalle

  return (
    <div className="space-y-3">
      <div>
        <label htmlFor={`${idCampo}-motivo`} className="block text-xs font-medium text-gray-700 mb-1">
          {etiqueta} <span className="text-red-600">*</span>
        </label>
        <select id={`${idCampo}-motivo`} value={valor.idMotivo} onChange={elegir} autoFocus={autoFocus}
          disabled={!motivos} className={campoCls}>
          <option value="">{fallo ? 'No se pudieron cargar los motivos' : motivos ? 'Elige un motivo…' : 'Cargando…'}</option>
          {motivos?.map(m => <option key={m.id} value={m.id}>{m.nombre}</option>)}
        </select>
      </div>
      {verTexto && (
        <div>
          <label htmlFor={`${idCampo}-texto`} className="block text-xs font-medium text-gray-700 mb-1">
            {pideTexto ? '¿Cuál?' : detalleEtiqueta} {pideTexto ? <span className="text-red-600">*</span> : <span className="text-gray-500 font-normal">(opcional)</span>}
          </label>
          <textarea id={`${idCampo}-texto`} rows={3} maxLength={500} value={valor.texto}
            onChange={e => onChange({ ...valor, texto: e.target.value })}
            placeholder={pideTexto ? 'Escribe el motivo' : 'Aclara si hace falta'}
            className={campoCls} />
        </div>
      )}
    </div>
  )
}
