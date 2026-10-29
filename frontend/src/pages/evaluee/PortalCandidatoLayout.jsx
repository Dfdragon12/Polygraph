import { useState } from 'react'

// Marco del portal del candidato: menú lateral NEGRO como el de los portales internos (slate-900,
// ítem activo coral) con el logo, la empresa, los datos del candidato y el aviso de estado de cada
// sección; el contenido a la derecha sobre gris muy claro. Sin degradados ni fondo del Login.
// En celular: barra blanca arriba y menú fijo abajo.

/* ── Íconos ── */
const svg = (d, c = 'w-5 h-5', sw = 1.7) => (
  <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={sw}>
    {[].concat(d).map((p, i) => <path key={i} strokeLinecap="round" strokeLinejoin="round" d={p} />)}
  </svg>
)
export const Icono = {
  inicio: (c) => svg('M2.25 12l8.954-8.955a1.126 1.126 0 011.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25', c),
  hoja: (c) => svg('M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z', c),
  documento: (c) => svg('M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z', c),
  calendario: (c) => svg('M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5', c),
  candado: (c) => svg('M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z', c),
  flecha: (c) => svg('M8.25 4.5l7.5 7.5-7.5 7.5', c, 2),
  atras: (c) => svg('M15.75 19.5L8.25 12l7.5-7.5', c, 2),
  check: (c) => svg('M4.5 12.75l6 6 9-13.5', c, 2.5),
  alerta: (c) => svg('M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z', c),
  ayuda: (c) => svg('M9.879 7.519c1.171-1.025 3.071-1.025 4.242 0 1.172 1.025 1.172 2.687 0 3.712-.203.179-.43.326-.67.442-.745.361-1.45.999-1.45 1.827v.75M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9 5.25h.008v.008H12v-.008z', c),
  escudo: (c) => svg('M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z', c),
  lugar: (c) => svg(['M15 10.5a3 3 0 11-6 0 3 3 0 016 0z', 'M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z'], c),
}

/** "ARNOLDO CASTRO" → "Arnoldo Castro" (los nombres a veces llegan en mayúsculas). */
export function nombrePropio(texto) {
  return (texto ?? '').toLowerCase().replace(/(^|\s)\S/g, (l) => l.toUpperCase()).trim()
}

// Antes de enviar la hoja de vida solo se ven Inicio y Hoja de vida; Documentos y Citas se
// habilitan al enviarla (el backend también lo exige: EvaluadoService.exigirHojaVidaEnviada).
export const seccionesVisibles = (bloqueado) => bloqueado ? SECCIONES.filter(s => s.id === 'inicio' || s.id === 'info') : SECCIONES

/** Aviso de lo que falta por habilitar. */
function AvisoBloqueo({ oscuro = false }) {
  return (
    <p className={`flex items-start gap-2 rounded-lg px-3 py-2.5 text-xs leading-relaxed ${
      oscuro ? 'bg-slate-800 text-slate-300' : 'bg-gray-50 text-gray-500'}`}>
      <span className={`mt-0.5 ${oscuro ? 'text-slate-400' : 'text-gray-400'}`}>{Icono.candado('h-4 w-4')}</span>
      Documentos y Citas se habilitan cuando envíes tu hoja de vida.
    </p>
  )
}

export const SECCIONES = [
  { id: 'inicio', etiqueta: 'Inicio', icono: Icono.inicio },
  { id: 'info', etiqueta: 'Hoja de vida', corta: 'Hoja', icono: Icono.hoja },
  { id: 'documentos', etiqueta: 'Documentos', corta: 'Docs', icono: Icono.documento },
  { id: 'citas', etiqueta: 'Citas', icono: Icono.calendario },
]

/** Logo: ícono + nombre. oscuro = sobre el menú negro. */
function Logo({ oscuro = false }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-gray-100 bg-white p-1.5 shadow-sm">
        <img src="/icon-192.png" alt="Polygraph" className="h-full w-full object-contain" />
      </div>
      <span className={`text-base font-semibold tracking-tight ${oscuro ? 'text-white' : 'text-gray-900'}`}>Polygraph Service</span>
    </div>
  )
}

// Avisos del menú sobre fondo negro
const PUNTO = { hecho: 'bg-emerald-400', atencion: 'bg-red-400', actual: 'bg-amber-400', pendiente: 'bg-slate-500' }
const AVISO = { hecho: 'text-emerald-300', atencion: 'text-red-300 font-semibold', actual: 'text-amber-300', pendiente: 'text-slate-400' }

/** Menú lateral (computador): empresa, quién es, qué estudio, y el menú con el aviso de estado de cada sección. */
export function PanelMarca({ info, vista, onIr, estados, bloqueado = false }) {
  const iniciales = `${(info.nombresEvaluado ?? '').trim()[0] ?? ''}${(info.apellidosEvaluado ?? '').trim()[0] ?? ''}`.toUpperCase()
  return (
    <aside className="hidden bg-slate-900 text-white lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-72 lg:flex-shrink-0 lg:flex-col">
      <div className="flex h-16 items-center border-b border-slate-700 px-6">
        <Logo oscuro />
      </div>

      <div className="border-b border-slate-700 px-6 py-5">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Portal del candidato</p>
        <div className="mt-3 flex items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-600 text-sm font-bold text-white">
            {iniciales || '—'}
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-white">{nombrePropio(`${info.nombresEvaluado ?? ''} ${info.apellidosEvaluado ?? ''}`)}</p>
            <p className="text-xs text-slate-400">CC {info.cedulaEvaluado}</p>
          </div>
        </div>
        <dl className="mt-4 space-y-1.5 text-xs">
          {/* La empresa va tal cual (siglas como S.A.S.); estudio y cargo a veces llegan en mayúsculas. */}
          {[['Empresa', info.empresa], ['Estudio', nombrePropio(info.proceso)], ['Cargo', nombrePropio(info.cargo)]].filter(([, v]) => v).map(([k, v]) => (
            <div key={k} className="flex gap-2">
              <dt className="w-14 shrink-0 text-slate-400">{k}</dt>
              <dd className="min-w-0 truncate font-medium text-slate-100" title={v}>{v}</dd>
            </div>
          ))}
        </dl>
      </div>

      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4" aria-label="Secciones">
        {seccionesVisibles(bloqueado).map(s => {
          const sel = vista === s.id
          const e = estados[s.id]
          return (
            <button key={s.id} type="button" onClick={() => onIr(s.id)} aria-current={sel ? 'page' : undefined}
              className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors ${
                sel ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}>
              <span className={sel ? 'text-white' : 'text-slate-400'}>{s.icono('h-5 w-5')}</span>
              <span className="flex-1 text-sm font-medium">{s.etiqueta}</span>
              {e && (
                <span className={`flex items-center gap-1.5 text-xs ${sel ? 'text-white/90' : (AVISO[e.tono] ?? AVISO.pendiente)}`}>
                  <span className={`h-1.5 w-1.5 rounded-full ${sel ? 'bg-white' : (PUNTO[e.tono] ?? PUNTO.pendiente)}`} />
                  {e.texto}
                </span>
              )}
            </button>
          )
        })}
        {bloqueado && <div className="pt-3"><AvisoBloqueo oscuro /></div>}
      </nav>

      <div className="border-t border-slate-700 px-6 py-4">
        <p className="flex items-start gap-2 text-[11px] leading-relaxed text-slate-400">
          <span className="mt-0.5">{Icono.escudo('h-4 w-4')}</span>
          Tus datos viajan cifrados y solo Polygraph Service los usa para este estudio, conforme a la Ley 1581 de 2012.
        </p>
      </div>
    </aside>
  )
}

/** Barra superior (celular y tablet): blanca, con el logo. */
export function Encabezado({ info, onIr }) {
  return (
    <header className="sticky top-0 z-20 border-b border-gray-200 bg-white lg:hidden">
      <div className="flex items-center justify-between px-4 py-3 sm:px-6">
        <button type="button" onClick={() => onIr('inicio')} aria-label="Ir al inicio"><Logo /></button>
        <span className="max-w-[40%] truncate text-xs text-gray-500">{nombrePropio(info.nombresEvaluado)}</span>
      </div>
    </header>
  )
}

/** Menú fijo abajo — celular y tablet. */
export function NavInferior({ vista, onIr, alertas = {}, bloqueado = false }) {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-gray-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden" aria-label="Secciones">
      <div className={`grid ${bloqueado ? 'grid-cols-2' : 'grid-cols-4'}`}>
        {seccionesVisibles(bloqueado).map(s => {
          const sel = vista === s.id
          return (
            <button key={s.id} type="button" onClick={() => onIr(s.id)} aria-current={sel ? 'page' : undefined}
              className={`relative flex flex-col items-center gap-0.5 py-2.5 text-[11px] font-medium ${sel ? 'text-primary-600' : 'text-gray-400'}`}>
              {sel && <span className="absolute top-0 h-0.5 w-8 rounded-full bg-primary-600" />}
              {s.icono('h-6 w-6')}
              {s.corta ?? s.etiqueta}
              {alertas[s.id] && <span className="absolute right-[calc(50%-18px)] top-2 h-2 w-2 rounded-full bg-red-500 ring-2 ring-white" />}
            </button>
          )
        })}
      </div>
    </nav>
  )
}

/** Título de cada página interna, con "volver al inicio". */
export function CabeceraSeccion({ titulo, descripcion, onVolver }) {
  return (
    <div className="mb-6">
      <button type="button" onClick={onVolver}
        className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-gray-500 hover:text-primary-600">
        {Icono.atras('h-4 w-4')} Inicio
      </button>
      <h1 className="text-2xl font-bold tracking-tight text-gray-900 sm:text-[28px]">{titulo}</h1>
      {descripcion && <p className="mt-1.5 text-[15px] leading-relaxed text-gray-500">{descripcion}</p>}
    </div>
  )
}

const TONO = {
  hecho:    { chip: 'bg-emerald-50 text-emerald-700', icono: 'bg-emerald-50 text-emerald-600', barra: 'bg-emerald-500' },
  atencion: { chip: 'bg-red-50 text-red-700',         icono: 'bg-red-50 text-red-600',         barra: 'bg-red-500' },
  actual:   { chip: 'bg-primary-50 text-primary-700', icono: 'bg-primary-50 text-primary-600', barra: 'bg-primary-500' },
  pendiente:{ chip: 'bg-gray-100 text-gray-600',      icono: 'bg-gray-100 text-gray-500',      barra: 'bg-gray-300' },
}

/** Tarjeta grande de la pantalla de inicio. */
function TarjetaInicio({ icono, titulo, estado, detalle, accion, porcentaje, onClick }) {
  const t = TONO[estado.tono] ?? TONO.pendiente
  return (
    <button type="button" onClick={onClick}
      className="group flex h-full flex-col rounded-2xl border border-gray-200 bg-white p-6 text-left shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <span className={`flex h-12 w-12 items-center justify-center rounded-xl ${t.icono}`}>{icono}</span>
        <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${t.chip}`}>
          {estado.tono === 'hecho' && Icono.check('h-3 w-3')}
          {estado.tono === 'atencion' && Icono.alerta('h-3.5 w-3.5')}
          {estado.texto}
        </span>
      </div>
      <p className="mt-5 text-lg font-semibold text-gray-900">{titulo}</p>
      <p className="mt-1 flex-1 text-sm leading-relaxed text-gray-500">{detalle}</p>
      {porcentaje != null && (
        <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-gray-100">
          <div className={`h-full rounded-full ${t.barra}`} style={{ width: `${porcentaje}%` }} />
        </div>
      )}
      <span className="mt-5 inline-flex items-center gap-1 text-sm font-semibold text-primary-600 transition-all group-hover:gap-2">
        {accion} {Icono.flecha('h-4 w-4')}
      </span>
    </button>
  )
}

/** Pantalla de inicio: qué le falta, sus dos tarjetas y su próxima cita. */
export function Inicio({ info, resumen, cita, onIr, bloqueado = false }) {
  const [viendoAutorizacion, setViendoAutorizacion] = useState(false)
  const nombre = nombrePropio(info.nombresEvaluado).split(' ')[0] || 'candidato'
  const pendientes = (bloqueado ? [resumen.hojaVida] : [resumen.hojaVida, resumen.documentos, resumen.cita])
    .filter(e => e.tono === 'actual' || e.tono === 'atencion').length
  const titular = pendientes === 0
    ? 'Todo está al día. Nuestro equipo avanza con tu estudio.'
    : `Tienes ${pendientes} ${pendientes === 1 ? 'tarea pendiente' : 'tareas pendientes'}.`

  return (
    <div>
      <section className="mb-8">
        <p className="text-sm font-semibold text-primary-600">Hola, {nombre}</p>
        <h1 className="mt-1 text-[28px] font-bold leading-tight tracking-tight text-gray-900 sm:text-[34px]">{titular}</h1>
        <p className="mt-2 text-[15px] text-gray-500">Desde aquí completas tu hoja de vida, cargas tus documentos y consultas tus citas.</p>
        {/* En computador estos datos ya están en el panel de la izquierda. */}
        <div className="mt-4 flex flex-wrap gap-2 text-sm text-gray-500 lg:hidden">
          {info.empresa && <span className="rounded-full bg-gray-100 px-3 py-1">Empresa: <span className="font-medium text-gray-700">{info.empresa}</span></span>}
          {info.proceso && <span className="rounded-full bg-gray-100 px-3 py-1">Estudio: <span className="font-medium text-gray-700">{nombrePropio(info.proceso)}</span></span>}
          {info.cargo && <span className="rounded-full bg-gray-100 px-3 py-1">Cargo: <span className="font-medium text-gray-700">{nombrePropio(info.cargo)}</span></span>}
        </div>
      </section>

      <div className="grid gap-5 sm:grid-cols-2">
        <TarjetaInicio icono={Icono.hoja('h-6 w-6')} titulo="Hoja de vida" estado={resumen.hojaVida}
          detalle={resumen.hojaVida.detalle} accion={resumen.hojaVida.accion} porcentaje={resumen.hojaVida.porcentaje}
          onClick={() => onIr('info')} />
        {bloqueado ? (
          <div className="flex h-full flex-col rounded-2xl border-2 border-dashed border-gray-200 bg-white/60 p-6">
            <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 text-gray-400">{Icono.candado('h-6 w-6')}</span>
            <p className="mt-5 text-lg font-semibold text-gray-900">Documentos y citas</p>
            <p className="mt-1 flex-1 text-sm leading-relaxed text-gray-500">
              Se habilitan cuando termines y envíes tu hoja de vida. Ahí podrás cargar tus soportes y ver tus citas.
            </p>
            <div className="mt-4 flex gap-2 text-xs text-gray-400">
              <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-1">{Icono.documento('h-3.5 w-3.5')} Documentos</span>
              <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-1">{Icono.calendario('h-3.5 w-3.5')} Citas</span>
            </div>
          </div>
        ) : (
          <TarjetaInicio icono={Icono.documento('h-6 w-6')} titulo="Documentos" estado={resumen.documentos}
            detalle={resumen.documentos.detalle} accion={resumen.documentos.accion}
            onClick={() => onIr('documentos')} />
        )}
      </div>

      {/* Próxima cita, a lo ancho */}
      {!bloqueado && <button type="button" onClick={() => onIr('citas')}
        className="group mt-5 flex w-full flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-6 text-left shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md sm:flex-row sm:items-center">
        {cita ? (
          <div className="flex w-full items-center gap-4 sm:w-auto">
            <div className="flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-2xl bg-primary-50 text-primary-700">
              <span className="text-[10px] font-semibold uppercase tracking-wider text-primary-500">
                {new Date(cita.fechaProgramada).toLocaleDateString('es-CO', { month: 'short' }).replace('.', '')}
              </span>
              <span className="text-2xl font-bold leading-none">{new Date(cita.fechaProgramada).getDate()}</span>
            </div>
            <div className="min-w-0">
              <p className="text-xs font-semibold uppercase tracking-wider text-primary-600">Tu próxima cita</p>
              <p className="text-lg font-semibold text-gray-900">{cita.nombre}</p>
              <p className="text-sm capitalize text-gray-500">
                {new Date(cita.fechaProgramada).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' })}
                {' · '}{new Date(cita.fechaProgramada).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })}
              </p>
              {cita.lugar && <p className="mt-0.5 flex items-center gap-1 truncate text-sm text-gray-500">{Icono.lugar('h-4 w-4 shrink-0')}{cita.lugar}</p>}
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-4">
            <span className={`flex h-12 w-12 items-center justify-center rounded-xl ${(TONO[resumen.cita.tono] ?? TONO.pendiente).icono}`}>{Icono.calendario('h-6 w-6')}</span>
            <div>
              <p className="text-lg font-semibold text-gray-900">Citas</p>
              <p className="text-sm text-gray-500">{resumen.cita.detalle}</p>
            </div>
          </div>
        )}
        <span className="inline-flex items-center gap-1 text-sm font-semibold text-primary-600 transition-all group-hover:gap-2 sm:ml-auto">
          {cita ? 'Ver detalle' : resumen.cita.accion} {Icono.flecha('h-4 w-4')}
        </span>
      </button>}

      {/* Autorización de datos: se consulta, no se cambia */}
      {info.autorizacionDatos && (
        <div className="mt-5 flex flex-col gap-3 rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5 sm:flex-row sm:items-center">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-emerald-600 ring-1 ring-emerald-200">{Icono.escudo('h-5 w-5')}</span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-gray-900">Autorización de datos aceptada</p>
            <p className="text-xs text-gray-600">
              {info.fechaAutorizacion ? `La aceptaste el ${fechaHoraLarga(info.fechaAutorizacion)}. ` : ''}Queda registrada y no se puede modificar.
            </p>
          </div>
          <button type="button" onClick={() => setViendoAutorizacion(true)}
            className="inline-flex items-center gap-1 text-sm font-semibold text-emerald-700 hover:text-emerald-800">
            Ver autorización {Icono.flecha('h-4 w-4')}
          </button>
        </div>
      )}
      {viendoAutorizacion && <ModalAutorizacion info={info} onCerrar={() => setViendoAutorizacion(false)} />}

      <div className="mt-8 flex gap-3 rounded-2xl border border-gray-200 bg-white p-5">
        <span className="text-gray-400">{Icono.ayuda('h-5 w-5')}</span>
        <p className="text-sm leading-relaxed text-gray-600">
          <span className="font-semibold text-gray-800">¿Tienes dudas?</span> Comunícate con la persona de la empresa que te
          envió este enlace. Si no puedes asistir a una cita, propón otro horario desde Citas.
        </p>
      </div>
    </div>
  )
}

export function Pie() {
  return (
    <footer className="mt-12 border-t border-gray-200">
      <div className="mx-auto flex max-w-3xl flex-col items-center justify-between gap-1 px-4 py-6 text-xs text-gray-400 sm:flex-row sm:px-8">
        <p>© {new Date().getFullYear()} Polygraph Service Ltda.</p>
        <p className="inline-flex items-center gap-1.5">{Icono.candado('h-3.5 w-3.5')} Datos protegidos · Ley 1581 de 2012</p>
      </div>
    </footer>
  )
}

/** Texto de la autorización — el mismo en la pantalla para aceptarla y en la consulta posterior. */
function TextoAutorizacion({ info }) {
  const nombre = nombrePropio(`${info.nombresEvaluado ?? ''} ${info.apellidosEvaluado ?? ''}`)
  return (
    <>
      <div className="max-h-72 space-y-3 overflow-y-auto rounded-xl border border-gray-200 bg-gray-50 p-5 text-sm leading-relaxed text-gray-700">
        <p>
          Yo, <strong>{nombre}</strong>, identificado(a) con cédula <strong>{info.cedulaEvaluado}</strong>, autorizo de manera libre,
          previa, expresa e informada a <strong>Polygraph Service Ltda</strong> para recolectar, almacenar, usar, circular, suprimir
          y en general tratar mis datos personales con la finalidad de adelantar el proceso de verificación de información,
          incluida la consulta de mi información financiera y crediticia en centrales de riesgo, conforme a la Ley 1266 de 2008,
          y la verificación de mis antecedentes, estudios, experiencia laboral y referencias.
        </p>
        <p>Esta autorización incluye datos de naturaleza sensible y financiera conforme a la Ley 1581 de 2012 y sus decretos reglamentarios.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        {[
          ['Qué datos', 'Tu hoja de vida, documentos, antecedentes, información financiera, referencias y la información de tus citas.'],
          ['Para qué', 'Solo para adelantar este estudio de verificación.'],
          ['Tus derechos', 'Conocer, actualizar y rectificar tu información (Leyes 1581 de 2012 y 1266 de 2008).'],
        ].map(([t, d]) => (
          <div key={t} className="rounded-xl border border-gray-200 px-4 py-3">
            <p className="text-xs font-semibold text-gray-900">{t}</p>
            <p className="mt-0.5 text-xs leading-relaxed text-gray-500">{d}</p>
          </div>
        ))}
      </div>
    </>
  )
}

const fechaHoraLarga = (iso) => iso
  ? new Date(iso).toLocaleString('es-CO', { day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit' })
  : ''

/** Consulta de la autorización ya aceptada: solo lectura, el candidato no la puede cambiar. */
function ModalAutorizacion({ info, onCerrar }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCerrar} />
      <div className="relative max-h-[90vh] w-full overflow-y-auto rounded-t-2xl bg-white shadow-xl sm:max-w-2xl sm:rounded-2xl">
        <div className="flex items-start gap-3 border-b border-gray-100 px-6 py-5">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">{Icono.escudo('h-5 w-5')}</span>
          <div className="min-w-0 flex-1">
            <h3 className="text-base font-semibold text-gray-900">Autorización de tratamiento de datos</h3>
            <p className="text-sm text-emerald-700">{info.fechaAutorizacion ? `Aceptada el ${fechaHoraLarga(info.fechaAutorizacion)}` : 'Aceptada'}</p>
          </div>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600">✕</button>
        </div>
        <div className="space-y-4 px-6 py-5">
          <TextoAutorizacion info={info} />
          <p className="flex items-start gap-2 rounded-xl bg-gray-50 px-4 py-3 text-xs leading-relaxed text-gray-500">
            <span className="mt-0.5 text-gray-400">{Icono.candado('h-4 w-4')}</span>
            Esta autorización quedó registrada y no se puede modificar desde aquí. Si quieres ejercer tus derechos
            (conocer, actualizar, rectificar o revocar), comunícate con quien te envió este enlace.
          </p>
        </div>
        <div className="flex justify-end border-t border-gray-100 px-6 py-4">
          <button type="button" onClick={onCerrar}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">Cerrar</button>
        </div>
      </div>
    </div>
  )
}

/** "Antes de iniciar": va después de la autorización y antes del portal, mientras el candidato no
 *  haya enviado su hoja de vida. Mismo marco que PantallaAutorizacion. */
export function PantallaAntesDeIniciar({ info, onContinuar }) {
  const nombre = nombrePropio(`${info.nombresEvaluado ?? ''} ${info.apellidosEvaluado ?? ''}`)
  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3 sm:px-6">
          <Logo />
          <span className="inline-flex items-center gap-1.5 text-xs text-gray-500">{Icono.candado('h-3.5 w-3.5')} Conexión segura</span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6 sm:py-12">
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
          <div className="border-b border-gray-100 px-6 py-6 sm:px-8">
            <div className="flex items-start gap-4">
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-600">{Icono.escudo('h-6 w-6')}</span>
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-wider text-primary-600">{nombre}</p>
                <h1 className="mt-1 text-xl font-bold tracking-tight text-gray-900 sm:text-2xl">Antes de iniciar</h1>
              </div>
            </div>
          </div>

          <div className="space-y-4 px-6 py-6 text-sm leading-relaxed text-gray-700 sm:px-8">
            <p>
              Por seguridad, la sesión se cierra después de un tiempo sin actividad. Guarde antes de salir de cada paso.
            </p>
            <p>
              Antes de comenzar, asegúrese de tener disponibles todos los documentos solicitados y la
              información necesaria para completar el proceso.
            </p>
            <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3.5">
              <p className="font-semibold text-gray-900">Recuerde que debe realizar dos acciones:</p>
              <ol className="mt-2 list-decimal space-y-1 pl-5">
                <li>Registrar la información correspondiente en los campos asignados de la plataforma.</li>
                <li>Adjuntar los documentos solicitados.</li>
              </ol>
            </div>
            <p>
              La información debe ser completa, correcta y actualizada. No basta con adjuntar los documentos;
              la información también debe ser diligenciada en los campos correspondientes.
            </p>
            <p className="font-medium text-gray-900">Verifique que cuenta con toda la documentación antes de iniciar el proceso.</p>

            <div className="flex justify-end pt-2">
              <button type="button" onClick={onContinuar}
                className="rounded-xl bg-primary-600 px-6 py-2.5 text-sm font-semibold text-white shadow-md shadow-primary-600/25 transition-colors hover:bg-primary-700">
                He leído y deseo continuar
              </button>
            </div>
          </div>
        </div>
      </main>

      <Pie />
    </div>
  )
}

/** Primera pantalla al abrir el link: la autorización de tratamiento de datos (Ley 1581). Hasta
 *  que la acepte no ve el portal — es el permiso para manejar su información. */
export function PantallaAutorizacion({ info, onAceptar }) {
  const [acepta, setAcepta] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const nombre = nombrePropio(`${info.nombresEvaluado ?? ''} ${info.apellidosEvaluado ?? ''}`)

  const aceptar = async () => {
    setEnviando(true)
    setError(null)
    try {
      await onAceptar()
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo registrar tu autorización. Intenta de nuevo.')
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3 sm:px-6">
          <Logo />
          <span className="inline-flex items-center gap-1.5 text-xs text-gray-500">{Icono.candado('h-3.5 w-3.5')} Conexión segura</span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6 sm:py-12">
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
          {/* Encabezado */}
          <div className="border-b border-gray-100 px-6 py-6 sm:px-8">
            <div className="flex items-start gap-4">
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-600">{Icono.escudo('h-6 w-6')}</span>
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-wider text-primary-600">Antes de empezar</p>
                <h1 className="mt-1 text-xl font-bold tracking-tight text-gray-900 sm:text-2xl">Autorización de tratamiento de datos personales</h1>
                <p className="mt-2 text-sm leading-relaxed text-gray-500">
                  Hola, <span className="font-medium text-gray-700">{nombre}</span>.
                  {info.empresa ? <> <span className="font-medium text-gray-700">{info.empresa}</span> solicitó</> : ' Se solicitó'} a Polygraph Service
                  un estudio{info.proceso ? <> de <span className="font-medium text-gray-700">{nombrePropio(info.proceso)}</span></> : ''}
                  {info.cargo ? <> para el cargo de <span className="font-medium text-gray-700">{nombrePropio(info.cargo)}</span></> : ''}.
                  Para continuar necesitamos tu autorización para manejar tu información.
                </p>
              </div>
            </div>
          </div>

          {/* Texto de la autorización */}
          <div className="space-y-5 px-6 py-6 sm:px-8">
            <TextoAutorizacion info={info} />

            <label className={`flex cursor-pointer items-start gap-3 rounded-xl border px-4 py-3.5 transition-colors ${
              acepta ? 'border-primary-300 bg-primary-50/60' : 'border-gray-200 hover:bg-gray-50'}`}>
              <input type="checkbox" className="mt-0.5 h-4 w-4 accent-primary-600" checked={acepta}
                onChange={(e) => setAcepta(e.target.checked)} />
              <span className="text-sm text-gray-800">
                He leído y <span className="font-semibold">acepto la autorización</span> para el tratamiento de mis datos personales.
              </span>
            </label>

            {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

            <div className="flex flex-col-reverse items-stretch gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs text-gray-400">Si no estás de acuerdo, no continúes y comunícate con quien te envió este enlace.</p>
              <button type="button" onClick={aceptar} disabled={!acepta || enviando}
                className="shrink-0 rounded-xl bg-primary-600 px-6 py-2.5 text-sm font-semibold text-white shadow-md shadow-primary-600/25 transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none">
                {enviando ? 'Registrando…' : 'Acepto y continuar'}
              </button>
            </div>
          </div>
        </div>
      </main>

      <Pie />
    </div>
  )
}

/** Pantalla completa para "validando enlace" o un link que ya no sirve. */
/** Primera pantalla del link: el candidato escribe su número de documento para entrar
 *  (AccesoCandidatoService). 3 intentos equivocados bloquean el link. */
export function PantallaDocumento({ onVerificar }) {
  const [documento, setDocumento] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  const entrar = async (e) => {
    e.preventDefault()
    if (!documento.trim()) { setError('Escribe tu número de documento.'); return }
    setEnviando(true)
    setError(null)
    try {
      await onVerificar(documento.trim())
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No pudimos verificar tu documento. Intenta de nuevo.')
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3 sm:px-6">
          <Logo />
          <span className="inline-flex items-center gap-1.5 text-xs text-gray-500">{Icono.candado('h-3.5 w-3.5')} Conexión segura</span>
        </div>
      </header>
      <div className="flex flex-1 items-center justify-center px-4 py-12">
        <form onSubmit={entrar} className="w-full max-w-md rounded-2xl border border-gray-200 bg-white px-6 py-8 shadow-sm sm:px-8">
          <span className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-50 text-primary-600">{Icono.escudo('h-7 w-7')}</span>
          <h1 className="text-center text-xl font-bold text-gray-900">Confirma que eres tú</h1>
          <p className="mt-2 text-center text-[15px] leading-relaxed text-gray-500">
            Este enlace es personal. Para entrar, escribe el número de tu documento de identidad.
          </p>
          <label htmlFor="documento" className="mt-6 block text-sm font-medium text-gray-700">Número de documento</label>
          <input id="documento" autoFocus autoComplete="off" inputMode="text" maxLength={30} value={documento}
            onChange={(e) => setDocumento(e.target.value)} placeholder="Ej. 1234567890"
            className="mt-1 h-12 w-full rounded-lg border border-gray-300 px-4 text-base tracking-wide focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <p className="mt-1.5 text-xs text-gray-400">Sin puntos ni espacios. Es el mismo documento con el que te registraron para este estudio.</p>
          {error && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <button type="submit" disabled={enviando}
            className="mt-6 h-12 w-full rounded-xl bg-primary-600 text-sm font-semibold text-white transition-colors hover:bg-primary-700 disabled:opacity-60">
            {enviando ? 'Verificando…' : 'Entrar'}
          </button>
          <p className="mt-4 text-center text-xs text-gray-400">Por seguridad, después de 3 intentos equivocados el enlace se bloquea.</p>
        </form>
      </div>
    </div>
  )
}

export function PantallaEstado({ cargando, titulo, mensaje }) {
  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="border-b border-gray-200 bg-white px-4 py-3 sm:px-6"><Logo /></header>
      <div className="flex flex-1 items-center justify-center px-4 py-16">
        <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white px-8 py-10 text-center shadow-sm">
          {cargando ? (
            <>
              <div className="mx-auto mb-5 h-10 w-10 animate-spin rounded-full border-[3px] border-gray-100 border-t-primary-600" />
              <p className="text-base font-medium text-gray-800">Validando tu enlace…</p>
              <p className="mt-1 text-sm text-gray-400">Esto toma solo un momento.</p>
            </>
          ) : (
            <>
              <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-gray-50 text-gray-400">{Icono.candado('h-7 w-7')}</div>
              <h1 className="text-xl font-bold text-gray-900">{titulo}</h1>
              <p className="mt-2 text-[15px] leading-relaxed text-gray-500">{mensaje}</p>
              <p className="mt-6 text-sm text-gray-400">Si crees que es un error, comunícate con quien te envió el enlace.</p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
