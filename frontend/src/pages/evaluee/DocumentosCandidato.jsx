import { useState } from 'react'

// Documentos del candidato con su estado de revisión. Lo usan la pestaña "Documentos" del portal
// y el paso "Documentos" del formulario — un solo lugar, así nunca muestran cosas distintas.
//
// archivos: { [clave]: { idDocumento, nombreArchivo, estadoRevision, observacionRevision, subiendo, error } }

const esDevolucion = (e) => !!e && e !== 'PENDIENTE' && e !== 'APROBADO'

const ETIQUETA_DEVOLUCION = {
  DEVUELTO: 'Devuelto', DENEGADO: 'Denegado', ILEGIBLE: 'Ilegible', VENCIDO: 'Vencido',
  INCOMPLETO: 'Incompleto', NO_CORRESPONDE: 'No corresponde',
}

/* ── Íconos ── */
const svg = (d, c, sw = 1.7) => (
  <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={sw}>
    {[].concat(d).map((p, i) => <path key={i} strokeLinecap="round" strokeLinejoin="round" d={p} />)}
  </svg>
)
const Icono = {
  documento: (c) => svg('M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z', c),
  subir: (c) => svg('M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5', c),
  check: (c) => svg('M4.5 12.75l6 6 9-13.5', c, 2.5),
  reloj: (c) => svg('M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z', c),
  alerta: (c) => svg('M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z', c),
  clip: (c) => svg('M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13', c),
  escudo: (c) => svg('M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z', c),
}

// Solo PDF o imagen (JPG/PNG). El backend revisa además el contenido real del archivo
// (ValidadorArchivoCandidato); aquí se avisa antes de subir para no gastar datos del candidato.
const ACEPTA = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'
const esPdf = (f) => /\.pdf$/i.test(f.name)
const esImagen = (f) => /\.(jpe?g|png)$/i.test(f.name)

/** null si el archivo sirve; si no, el mensaje para el candidato. */
export function validarArchivo(file, { maxPdfMb = 5, maxImagenMb = 5 } = {}) {
  if (!esPdf(file) && !esImagen(file)) return 'Solo se aceptan archivos PDF o imágenes JPG/PNG.'
  if (file.size === 0) return 'El archivo está vacío.'
  const max = esPdf(file) ? maxPdfMb : maxImagenMb
  if (file.size > max * 1024 * 1024) {
    const mb = (file.size / (1024 * 1024)).toFixed(1)
    return esPdf(file)
      ? `El PDF pesa ${mb} MB y el máximo es ${max} MB. Escanéalo a menor resolución o en blanco y negro.`
      : `La imagen pesa ${mb} MB y el máximo es ${max} MB. Tómala con menor resolución.`
  }
  return null
}

export function resumenDocumentos(documentos, archivos) {
  const total = documentos.length
  const aprobados = documentos.filter(d => archivos[d.id]?.estadoRevision === 'APROBADO').length
  const devueltos = documentos.filter(d => esDevolucion(archivos[d.id]?.estadoRevision)).length
  const cargados = documentos.filter(d => archivos[d.id]?.idDocumento).length
  return { total, aprobados, devueltos, cargados }
}

/** Estado visual de un documento: sin cargar · en revisión · aprobado · devuelto. */
function estadoDe(a) {
  if (!a.idDocumento) return 'SIN_CARGAR'
  if (a.estadoRevision === 'APROBADO') return 'APROBADO'
  if (esDevolucion(a.estadoRevision)) return 'DEVUELTO'
  return 'PENDIENTE'
}

const ESTILO = {
  SIN_CARGAR: { chip: 'bg-gray-100 text-gray-600', etiqueta: 'Sin cargar', tile: 'bg-gray-100 text-gray-400', borde: 'border-gray-200', icono: Icono.documento },
  PENDIENTE:  { chip: 'bg-amber-50 text-amber-700 ring-1 ring-inset ring-amber-600/20', etiqueta: 'En revisión', tile: 'bg-amber-50 text-amber-600', borde: 'border-gray-200', icono: Icono.reloj },
  APROBADO:   { chip: 'bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-600/20', etiqueta: 'Aprobado', tile: 'bg-emerald-50 text-emerald-600', borde: 'border-emerald-200', icono: Icono.check },
  DEVUELTO:   { chip: 'bg-red-50 text-red-700 ring-1 ring-inset ring-red-600/20', etiqueta: 'Por corregir', tile: 'bg-red-50 text-red-600', borde: 'border-red-200', icono: Icono.alerta },
}

/** Anillo de avance: aprobados sobre el total. */
function Anillo({ valor, total }) {
  const r = 26
  const c = 2 * Math.PI * r
  const p = total ? valor / total : 0
  return (
    <div className="relative h-16 w-16 shrink-0">
      <svg className="h-16 w-16 -rotate-90" viewBox="0 0 64 64">
        <circle cx="32" cy="32" r={r} fill="none" strokeWidth="6" className="stroke-gray-100" />
        <circle cx="32" cy="32" r={r} fill="none" strokeWidth="6" strokeLinecap="round"
          className="stroke-emerald-500 transition-all duration-700" strokeDasharray={c} strokeDashoffset={c * (1 - p)} />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-gray-900 tabular-nums">{valor}/{total}</span>
    </div>
  )
}

function Contador({ valor, etiqueta, punto }) {
  return (
    <div className="flex items-center gap-2 rounded-xl bg-gray-50 px-3 py-2">
      <span className={`h-2 w-2 rounded-full ${punto}`} />
      <span className="text-sm font-semibold text-gray-900 tabular-nums">{valor}</span>
      <span className="text-xs text-gray-500">{etiqueta}</span>
    </div>
  )
}

/** Zona para soltar o elegir el archivo. tono: normal | correccion */
function ZonaCarga({ onArchivo, subiendo, tono = 'normal', reemplazo = false, limites }) {
  const [encima, setEncima] = useState(false)
  const rojo = tono === 'correccion'
  return (
    <label
      onDragOver={(e) => { e.preventDefault(); if (!subiendo) setEncima(true) }}
      onDragLeave={() => setEncima(false)}
      onDrop={(e) => { e.preventDefault(); setEncima(false); if (!subiendo) onArchivo(e.dataTransfer.files?.[0]) }}
      className={`group flex items-center gap-3 rounded-xl border-2 border-dashed px-4 py-3.5 transition-all ${
        subiendo ? 'cursor-wait border-gray-200 bg-gray-50'
          : encima ? (rojo ? 'cursor-pointer border-red-400 bg-red-50' : 'cursor-pointer border-primary-400 bg-primary-50')
          : rojo ? 'cursor-pointer border-red-200 bg-red-50/40 hover:border-red-300 hover:bg-red-50'
          : 'cursor-pointer border-gray-200 bg-gray-50/60 hover:border-primary-300 hover:bg-primary-50/50'}`}>
      <input type="file" className="hidden" disabled={subiendo} accept={ACEPTA}
        onChange={(e) => { onArchivo(e.target.files?.[0]); e.target.value = '' }} />
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white shadow-sm ring-1 ring-gray-200 transition-transform group-hover:-translate-y-0.5 ${
        rojo ? 'text-red-600' : 'text-primary-600'}`}>
        {subiendo
          ? <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
          : Icono.subir('h-5 w-5')}
      </span>
      <span className="min-w-0 text-sm">
        <span className={`block font-semibold ${rojo ? 'text-red-700' : 'text-gray-800'}`}>
          {subiendo ? 'Subiendo tu archivo…' : rojo ? 'Carga el documento corregido' : reemplazo ? 'Reemplazar archivo' : 'Selecciona o arrastra tu archivo'}
        </span>
        {!subiendo && (
          <span className="block text-xs text-gray-500">
            PDF hasta {limites.maxPdfMb} MB · JPG o PNG hasta {limites.maxImagenMb} MB · todo el documento visible y legible
          </span>
        )}
      </span>
    </label>
  )
}

function TarjetaDocumento({ doc, a, onSubir, onQuitar, limites }) {
  const estado = estadoDe(a)
  const e = ESTILO[estado]
  const cargado = estado !== 'SIN_CARGAR'
  const motivo = ETIQUETA_DEVOLUCION[a.estadoRevision]

  return (
    <li className={`rounded-2xl border bg-white p-4 shadow-sm transition-shadow hover:shadow-md sm:p-5 ${e.borde}`}>
      <div className="flex items-start gap-3.5">
        <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${e.tile}`}>{e.icono('h-5 w-5')}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <p className="text-[15px] font-semibold text-gray-900">{doc.nombre}</p>
            <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${e.chip}`}>
              {estado === 'DEVUELTO' && motivo ? motivo : e.etiqueta}
            </span>
          </div>
          <p className="mt-0.5 text-xs text-gray-500">
            {doc.obligatorio ? 'Obligatorio' : 'Opcional'}
            {estado === 'PENDIENTE' && ' · Nuestro equipo lo está revisando'}
            {estado === 'APROBADO' && ' · Revisado y aprobado por nuestro equipo'}
          </p>
        </div>
      </div>

      <div className="mt-4 space-y-3 sm:pl-[58px]">
        {estado === 'DEVUELTO' && a.observacionRevision && (
          <div className="flex gap-2.5 rounded-xl border border-red-100 bg-red-50 px-3.5 py-3">
            <span className="mt-0.5 text-red-500">{Icono.alerta('h-4 w-4')}</span>
            <div className="text-sm">
              <p className="font-semibold text-red-800">Qué debes corregir</p>
              <p className="mt-0.5 text-red-700">{a.observacionRevision}</p>
            </div>
          </div>
        )}

        {cargado && (
          <div className="flex items-center gap-2.5 rounded-xl bg-gray-50 px-3 py-2.5">
            <span className="text-gray-400">{Icono.clip('h-4 w-4')}</span>
            <span className={`min-w-0 flex-1 truncate text-sm ${estado === 'DEVUELTO' ? 'text-gray-400 line-through' : 'text-gray-700'}`} title={a.nombreArchivo}>
              {a.nombreArchivo}
            </span>
            {estado !== 'APROBADO' && !a.subiendo && (
              <button type="button" onClick={() => onQuitar(doc.id)}
                className="shrink-0 rounded-md px-2 py-1 text-xs font-medium text-gray-500 hover:bg-white hover:text-red-600">
                Quitar
              </button>
            )}
          </div>
        )}

        {estado !== 'APROBADO' && (
          <ZonaCarga onArchivo={(f) => onSubir(doc.id, f)} subiendo={a.subiendo} limites={limites}
            tono={estado === 'DEVUELTO' ? 'correccion' : 'normal'} reemplazo={estado === 'PENDIENTE'} />
        )}

        {a.error && <p className="text-xs font-medium text-red-600">{a.error}</p>}
      </div>
    </li>
  )
}

export default function DocumentosCandidato({ documentos, archivos, onSubir, onQuitar, mostrarPendientes, maxPdfMb = 5, maxImagenMb = 5 }) {
  const limites = { maxPdfMb, maxImagenMb }
  if (documentos.length === 0) {
    return (
      <div className="rounded-2xl border border-gray-200 bg-white px-6 py-10 text-center shadow-sm">
        <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">{Icono.check('h-6 w-6')}</span>
        <p className="text-sm font-semibold text-gray-800">Para este estudio no necesitas cargar documentos</p>
        <p className="mt-1 text-xs text-gray-500">No tienes nada pendiente en esta sección.</p>
      </div>
    )
  }
  const { total, aprobados, devueltos, cargados } = resumenDocumentos(documentos, archivos)
  const enRevision = cargados - aprobados - devueltos
  const pendientesObligatorios = documentos.filter(d => d.obligatorio && !archivos[d.id]?.idDocumento).length

  // Primero lo que necesita acción: devueltos, sin cargar, en revisión y al final los aprobados.
  const orden = { DEVUELTO: 0, SIN_CARGAR: 1, PENDIENTE: 2, APROBADO: 3 }
  const ordenados = [...documentos].sort((x, y) => orden[estadoDe(archivos[x.id] ?? {})] - orden[estadoDe(archivos[y.id] ?? {})])

  const mensaje = aprobados === total ? '¡Todos tus documentos fueron aprobados!'
    : devueltos > 0 ? `Tienes ${devueltos} documento${devueltos !== 1 ? 's' : ''} por corregir`
    : cargados === total ? 'Ya cargaste todo. Estamos revisando tus documentos.'
    : `Te falta${total - cargados !== 1 ? 'n' : ''} ${total - cargados} documento${total - cargados !== 1 ? 's' : ''} por cargar`

  return (
    <div className="space-y-5">
      {/* Resumen */}
      <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex items-center gap-4">
          <Anillo valor={aprobados} total={total} />
          <div className="min-w-0">
            <p className={`text-base font-semibold ${devueltos > 0 ? 'text-red-700' : aprobados === total ? 'text-emerald-700' : 'text-gray-900'}`}>{mensaje}</p>
            <p className="mt-0.5 text-xs text-gray-500">{aprobados} de {total} aprobados por nuestro equipo</p>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
          <Contador valor={total - cargados} etiqueta="por cargar" punto="bg-gray-300" />
          <Contador valor={enRevision} etiqueta="en revisión" punto="bg-amber-400" />
          <Contador valor={devueltos} etiqueta="por corregir" punto="bg-red-500" />
          <Contador valor={aprobados} etiqueta="aprobados" punto="bg-emerald-500" />
        </div>
      </div>

      <ul className="space-y-3">
        {ordenados.map(doc => (
          <TarjetaDocumento key={doc.id} doc={doc} a={archivos[doc.id] ?? {}} onSubir={onSubir} onQuitar={onQuitar} limites={limites} />
        ))}
      </ul>

      {mostrarPendientes && pendientesObligatorios > 0 && (
        <p className="text-xs font-medium text-red-600">
          Faltan {pendientesObligatorios} documento{pendientesObligatorios !== 1 ? 's' : ''} obligatorio{pendientesObligatorios !== 1 ? 's' : ''} por cargar.
        </p>
      )}

      <p className="flex items-start gap-2 text-xs leading-relaxed text-gray-400">
        <span className="mt-0.5">{Icono.escudo('h-4 w-4')}</span>
        Tus documentos solo los ve el equipo de Polygraph Service que realiza tu estudio. Si alguno se devuelve, aquí verás el motivo.
      </p>
    </div>
  )
}
