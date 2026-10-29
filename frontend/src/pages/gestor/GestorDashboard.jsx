import { useSemaforoSerie } from '../../hooks/useSemaforoSerie'
import { useState, useEffect, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import api from '../../services/api'
import tokenService from '../../services/tokenService'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import { ResponsiveContainer, AreaChart, Area, BarChart, Bar, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts'
import { EncabezadoPagina, EsqueletoPagina, AlertaError, botonPrimario, botonSecundario } from '../../components/admin/PaginaAdmin'
import {
  C, eje, tooltipEstilo, Icono, suma, saludo, fechaLarga, semanaCorta, rangoAgenda, resumirAgenda, contarPor, SeccionNovedades,
  Atencion, Grafica, Enlace, SinDatos, Cargando, Dona, BarrasHorizontales, BarraEstados, GraficaAgenda, porHoraHoy, GraficaHoras,
  Pestanas, usePestana, irAPestana,
} from '../../components/tablero/Tablero'

// Tablero del gestor en gráficas: el semáforo de sus servicios, las novedades internas que esperan
// su respuesta, su agenda (semana pasada y próximos 14 días) y la confirmación de las citas, cuántas
// solicitudes llegan por semana, cómo van las entregas, lo que él asigna (subprocesos de análisis:
// por asignar, carga por analista, hoy hora por hora), los links de candidato y la carga por cliente
// y por estudio.
// Los números vienen agrupados del backend (/gestor/dashboard/graficas); la agenda sale del mismo
// calendario de la pantalla Agenda.

const ESTADOS = [
  { clave: 'pendientes',    estado: 'PENDIENTE',    label: 'Pendiente',    color: C.ambar },
  { clave: 'programando',   estado: 'PROGRAMANDO',  label: 'Programando',  color: C.cielo },
  { clave: 'enEjecucion',   estado: 'EN_EJECUCION', label: 'En ejecución', color: C.indigo },
  { clave: 'reprogramados', estado: 'REPROGRAMADO', label: 'Reprogramado', color: C.naranja },
  { clave: 'finalizados',   estado: 'FINALIZADO',   label: 'Finalizado',   color: C.verde },
  { clave: 'publicados',    estado: 'PUBLICADO',    label: 'Publicado',    color: C.teal },
  { clave: 'cancelados',    estado: 'CANCELADO',    label: 'Cancelado',    color: C.gris },
]

export default function GestorDashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
  const [datos, setDatos] = useState(null)
  const [graficas, setGraficas] = useState(null)
  const [linkStats, setLinkStats] = useState(null)
  const [citas, setCitas] = useState(null)
  const [porAsignar, setPorAsignar] = useState(null)
  const [conteoAsig, setConteoAsig] = useState(null)   // subprocesos de análisis por estado
  const [asignados, setAsignados] = useState(null)     // análisis asignados en curso
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [pestana, setPestana] = usePestana('tablero-gestor-pestana', 'resumen')

  // Carga todo el tablero; también se llama sola cuando algo cambia (tiempo real), sin spinner.
  const cargarDatos = useCallback(() => {
    Promise.all([
      api.get('/gestor/dashboard').then(r => r.data),
      api.get('/gestor/dashboard/graficas').then(r => r.data).catch(() => null),
      tokenService.estadisticas().catch(() => null),
    ])
      .then(([dashboard, graf, stats]) => { setDatos(dashboard); setGraficas(graf); setLinkStats(stats) })
      .catch(() => setError('No se pudieron cargar los datos del tablero.'))
      .finally(() => setCargando(false))

    asignacionService.calendario(...rangoAgenda()).then(setCitas).catch(() => setCitas([]))

    asignacionService.conteo().then(setConteoAsig).catch(() => setConteoAsig(null))
    asignacionService.listar({ estado: 'EN_EJECUCION', size: 200 })
      .then(r => setAsignados(r.content ?? []))
      .catch(() => setAsignados([]))

    asignacionService.listar({ estado: 'PENDIENTE', size: 200 })
      .then(r => setPorAsignar({ items: r.content ?? [], total: r.totalElements ?? 0 }))
      .catch(() => setPorAsignar({ items: [], total: 0 }))
  }, [])

  useEffect(() => { cargarDatos() }, [cargarDatos])
  useCambios(cargarDatos, { esperaMs: 1500 })

  const ir = (ruta) => navigate(ruta)

  /* ── Series ── */
  const semaforo = ESTADOS.map(e => ({ name: e.label, value: datos?.[e.clave] ?? 0, color: e.color, clave: e.estado }))
  const enCurso = (datos?.pendientes ?? 0) + (datos?.programando ?? 0) + (datos?.enEjecucion ?? 0) + (datos?.reprogramados ?? 0)

  const solicitudes = (graficas?.solicitudesPorSemana ?? []).map(s => ({ semana: semanaCorta(s.inicio), cantidad: s.cantidad }))
  const totalSolicitudes = suma(solicitudes, 'cantidad')

  const e = graficas?.entregas
  const entregas = e ? [
    { name: 'Vencidas', value: e.vencidas, color: C.rojo },
    { name: 'Hoy', value: e.hoy, color: C.naranja },
    { name: 'Mañana', value: e.manana, color: C.ambar },
    { name: '2 a 7 días', value: e.estaSemana, color: C.cielo },
    { name: 'Más de 7 días', value: e.despues, color: C.verde },
    ...(e.sinFecha > 0 ? [{ name: 'Sin fecha', value: e.sinFecha, color: C.gris }] : []),
  ] : []

  const { lista: agendados, futuras, agenda, confirmacion, citasHoy } = useMemo(() => resumirAgenda(citas), [citas])
  const asignar = useMemo(() => contarPor(porAsignar?.items, a => a.subproceso), [porAsignar])
  const cargaAnalista = useMemo(() => contarPor(asignados, a => a.nombreAsignado ?? 'Sin analista', 8), [asignados])
  const horasHoy = useMemo(() => porHoraHoy(agendados.filter(c => c.rolResponsable === 'ANALISTA_INTERNO')), [agendados])
  const analisisHoy = suma(horasHoy, 'Análisis')
  const estadosAsig = useSemaforoSerie()

  const nov = graficas?.novedades

  const links = [
    { name: 'Esperando', value: linkStats?.pendientes ?? 0, color: C.ambar },
    { name: 'Hoja enviada', value: linkStats?.usados ?? 0, color: C.verde },
    { name: 'Cerrados', value: linkStats?.expirados ?? 0, color: C.gris },
    { name: 'Bloqueados', value: linkStats?.bloqueados ?? 0, color: C.rojo },
  ]

  const irPestana = (id) => irAPestana(setPestana, id)

  const PESTANAS = [
    { id: 'resumen', label: 'Resumen' },
    { id: 'novedades', label: 'Novedades', badge: nov?.abiertas, alerta: (nov?.abiertasMas48h ?? 0) > 0 },
    { id: 'asignacion', label: 'Asignación', badge: porAsignar?.total },
    { id: 'agenda', label: 'Agenda', badge: citasHoy || null },
    { id: 'clientes', label: 'Clientes y links' },
  ]

  return (
    <div className="mx-auto max-w-7xl">
      <EncabezadoPagina
        seccion="Panel del gestor"
        titulo={`${saludo()}, ${usuario?.nombre?.trim() || 'gestor'}`}
        descripcion={<span className="inline-block first-letter:uppercase">{fechaLarga()}</span>}
        icono={Icono.panel('h-6 w-6')}
        acciones={<>
          <button type="button" onClick={() => ir('/gestor/agenda')} className={botonSecundario}>{Icono.calendario('h-4 w-4')} Agenda</button>
          <button type="button" onClick={() => ir('/gestor/solicitudes')} className={botonPrimario}>{Icono.lista('h-4 w-4')} Solicitudes</button>
        </>}
      />

      <AlertaError mensaje={error} />

      {cargando ? <EsqueletoPagina indicadores={5} bloques={2} /> : (
        <div className="space-y-5">

          {/* ── Lo urgente ── */}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            <Atencion titulo="Novedades abiertas" valor={nov?.abiertas ?? 0}
              detalle={(nov?.abiertasMas48h ?? 0) > 0 ? `${nov.abiertasMas48h} con más de 48 h` : 'Esperan tu respuesta'}
              vacio="Ninguna abierta" icono={Icono.alerta('h-4 w-4')} tono={(nov?.abiertasMas48h ?? 0) > 0 ? 'rojo' : 'ambar'}
              onClick={() => irPestana('novedades')} />
            <Atencion titulo="Entregas vencidas" valor={e?.vencidas ?? 0} detalle="Pasaron su fecha" vacio="Ninguna vencida"
              icono={Icono.reloj('h-4 w-4')} tono="rojo" onClick={() => irPestana('resumen')} />
            <Atencion titulo="Sin asignar" valor={porAsignar?.total ?? 0} detalle="Subprocesos sin analista" vacio="Todo asignado"
              icono={Icono.persona('h-4 w-4')} tono="ambar" onClick={() => irPestana('asignacion')} />
            <Atencion titulo="Por concepto final" valor={datos?.finalizados ?? 0} detalle="Listos para publicar" vacio="Nada por publicar"
              icono={Icono.sello('h-4 w-4')} tono="primario" onClick={() => ir('/gestor/solicitudes?estado=FINALIZADO')} />
            <Atencion titulo="Citas hoy" valor={citasHoy} detalle="Poligrafías y visitas" vacio="Sin citas hoy"
              icono={Icono.calendario('h-4 w-4')} tono="primario" onClick={() => irPestana('agenda')} />
          </div>

          {/* ── Semáforo (barra lineal) ── */}
          <BarraEstados titulo="Semáforo de servicios" descripcion="Todos los servicios de tus clientes por estado · clic para ver"
            estados={semaforo} destacado={{ valor: enCurso, etiqueta: 'en curso' }}
            onClick={(x) => ir(`/gestor/solicitudes?estado=${x.clave}`)} />

          <Pestanas pestanas={PESTANAS} activa={pestana} onCambiar={setPestana} />

          {pestana === 'resumen' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
              <Grafica titulo="Solicitudes recibidas" descripcion={`Últimas 12 semanas · ${totalSolicitudes} en total`}>
                {totalSolicitudes === 0 ? <SinDatos texto="No llegaron solicitudes en estas semanas" /> : (
                  <ResponsiveContainer width="100%" height={240}>
                    <AreaChart data={solicitudes} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
                      <defs>
                        <linearGradient id="gradSolicitudes" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor={C.coral} stopOpacity={0.3} />
                          <stop offset="100%" stopColor={C.coral} stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid vertical={false} stroke="#f3f4f6" />
                      <XAxis dataKey="semana" tick={eje} axisLine={false} tickLine={false} interval="preserveStartEnd" />
                      <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
                      <Tooltip contentStyle={tooltipEstilo} formatter={(v) => [v, 'Solicitudes']} labelFormatter={(l) => `Semana del ${l}`} />
                      <Area type="linear" dataKey="cantidad" stroke={C.coral} strokeWidth={2.5} fill="url(#gradSolicitudes)"
                        dot={{ r: 3, fill: '#fff', stroke: C.coral, strokeWidth: 2 }} activeDot={{ r: 5 }} />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </Grafica>

              <Grafica titulo="Entregas de servicios en curso" descripcion="Qué tan cerca está la fecha de entrega al cliente"
                accion={<Enlace onClick={() => ir('/gestor/solicitudes')}>Ver solicitudes</Enlace>}>
                {!entregas.length || suma(entregas) === 0 ? <SinDatos texto="No tienes servicios en curso" /> : (
                  <ResponsiveContainer width="100%" height={240}>
                    <BarChart data={entregas} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
                      <CartesianGrid vertical={false} stroke="#f3f4f6" />
                      <XAxis dataKey="name" tick={eje} axisLine={false} tickLine={false} interval={0} />
                      <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
                      <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }} formatter={(v) => [v, 'Servicios']} />
                      <Bar dataKey="value" radius={[6, 6, 0, 0]} maxBarSize={56}>
                        {entregas.map(x => <Cell key={x.name} fill={x.color} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </Grafica>
            </div>
          )}

          {pestana === 'novedades' && (
            <SeccionNovedades nov={nov} onAbrir={(n) => ir(`/gestor/formulario/${n.idServicioSubproceso}`)}
              descripcion="Lo que reportan analistas, poligrafistas y visitadores en los subprocesos de tus clientes" />
          )}

          {pestana === 'asignacion' && (
            <div className="space-y-5">
              <BarraEstados titulo="Subprocesos de análisis" descripcion="Los que tú asignas a analistas · poligrafía y visita las agenda el programador"
                estados={estadosAsig} destacado={{ valor: estadosAsig.filter(e => ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO'].includes(e.clave)).reduce((s, e) => s + e.value, 0), etiqueta: 'en curso' }}
                onClick={() => ir('/gestor/agenda')} />
              <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
                <Grafica titulo="Por asignar" descripcion={`${porAsignar?.total ?? 0} sin responsable, por tipo`}
                  accion={(porAsignar?.total ?? 0) > 0 && <Enlace onClick={() => ir('/gestor/agenda')}>Asignar</Enlace>}>
                  {porAsignar === null ? <Cargando />
                    : <BarrasHorizontales datos={asignar} color={C.ambar} unidad="Sin asignar" vacio="Todo está asignado" />}
                </Grafica>
                <Grafica titulo="Carga por analista" descripcion="Subprocesos asignados en curso">
                  {asignados === null ? <Cargando />
                    : <BarrasHorizontales datos={cargaAnalista} color={C.indigo} unidad="Asignados" vacio="Ningún analista con trabajo en curso" />}
                </Grafica>
                <Grafica titulo="Hoy, hora por hora" descripcion={`${analisisHoy} análisis agendado${analisisHoy !== 1 ? 's' : ''} para hoy`}
                  accion={<Enlace onClick={() => ir('/gestor/agenda?seccion=cronograma')}>Cronograma</Enlace>}>
                  {citas === null ? <Cargando /> : analisisHoy === 0
                    ? <SinDatos texto="No hay análisis agendados para hoy" /> : <GraficaHoras datos={horasHoy} series={['Análisis']} />}
                </Grafica>
              </div>
            </div>
          )}

          {pestana === 'agenda' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
              <Grafica className="xl:col-span-3" titulo="Agenda" descripcion={`Semana pasada y próximos 14 días · ${futuras.length} ${futuras.length === 1 ? 'cita' : 'citas'} por venir`}
                accion={<Enlace onClick={() => ir('/gestor/agenda?seccion=cronograma')}>Abrir agenda</Enlace>}>
                {citas === null ? <Cargando /> : agendados.length === 0
                  ? <SinDatos texto="No hay nada agendado en estas fechas" /> : <GraficaAgenda agenda={agenda} />}
              </Grafica>
              <Grafica className="xl:col-span-2" titulo="Confirmación de asistencia" descripcion="Poligrafías y visitas de hoy a 14 días">
                {citas === null ? <Cargando /> : <Dona datos={confirmacion} total={futuras.length} etiquetaTotal="citas"
                  vacio="No hay citas por venir" onClick={() => ir('/gestor/agenda?seccion=cronograma')} />}
              </Grafica>
            </div>
          )}

          {pestana === 'clientes' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
              <Grafica titulo="Carga por cliente" descripcion="Servicios en curso · los 6 con más">
                <BarrasHorizontales datos={graficas?.porCliente ?? []} color={C.indigo} />
              </Grafica>
              <Grafica titulo="Carga por estudio" descripcion="Servicios en curso · los 6 más pedidos">
                <BarrasHorizontales datos={graficas?.porProceso ?? []} color={C.teal} />
              </Grafica>
              <Grafica titulo="Links de candidato" descripcion="En qué va cada candidato con su link">
                <Dona datos={links} total={linkStats?.total ?? suma(links)} etiquetaTotal="links" alto={170} />
              </Grafica>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
