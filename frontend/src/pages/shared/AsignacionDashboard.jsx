import { useSemaforoSerie } from '../../hooks/useSemaforoSerie'
import { useEffect, useMemo, useState, useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import api from '../../services/api'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import { ResponsiveContainer, BarChart, Bar, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts'
import { EncabezadoPagina, EsqueletoPagina, AlertaError, botonPrimario } from '../../components/admin/PaginaAdmin'
import {
  C, eje, tooltipEstilo, Icono, suma, saludo, fechaLarga, semanaCorta, resumirAgenda, contarPor, porHoraHoy, iso,
  Atencion, Grafica, Enlace, SinDatos, Cargando, Dona, BarrasHorizontales, BarraEstados, GraficaAgenda, GraficaHoras,
  SeccionNovedades, Pestanas, usePestana, irAPestana,
} from '../../components/tablero/Tablero'

// Tablero compartido de analista, poligrafista y visitador (el backend ya acota todo al usuario):
// el mismo formato que gestor y programador — arriba lo urgente y la barra de estado de su trabajo;
// debajo, en pestañas, el día de hoy, su agenda, su rendimiento y las novedades que él registró.
const CONFIG = {
  analista: {
    ruta: '/analista/asignaciones', boton: 'Mis asignaciones', serie: 'Análisis', unidad: 'subproceso',
    estado: 'Mis subprocesos de análisis', citas: false,
  },
  poligrafista: {
    ruta: '/poligrafista/servicios', boton: 'Mis servicios', serie: 'Poligrafía', unidad: 'poligrafía',
    estado: 'Mis poligrafías', citas: true,
  },
  visitador: {
    ruta: '/visitador/visitas', boton: 'Mis visitas', serie: 'Visita', unidad: 'visita',
    estado: 'Mis visitas domiciliarias', citas: true,
  },
}


export default function AsignacionDashboard() {
  const prefijo = useLocation().pathname.split('/')[1]
  const config = CONFIG[prefijo] ?? CONFIG.analista
  const { usuario } = useAuth()
  const navigate = useNavigate()

  const [conteos, setConteos] = useState(null)
  const [porHacer, setPorHacer] = useState(null)     // EN_EJECUCION
  const [historial, setHistorial] = useState(null)   // FINALIZADO + PUBLICADO
  const [nov, setNov] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [pestana, setPestana] = usePestana(`tablero-${prefijo}-pestana`, 'hoy')

  // Carga todo el tablero; también se llama sola cuando algo cambia (tiempo real), sin spinner.
  const cargarDatos = useCallback(() => {
    Promise.all([
      asignacionService.conteo(),
      asignacionService.listar({ estado: 'EN_EJECUCION', size: 200, sort: 'fechaProgramada,asc' }),
      asignacionService.listar({ estado: 'FINALIZADO', size: 100, sort: 'fechaCompletado,desc' }),
      asignacionService.listar({ estado: 'PUBLICADO', size: 100, sort: 'fechaCompletado,desc' }),
    ])
      .then(([c, asignados, completados, publicados]) => {
        setConteos(c)
        setPorHacer(asignados.content ?? [])
        setHistorial([...(completados.content ?? []), ...(publicados.content ?? [])])
      })
      .catch(() => setError('No se pudieron cargar los datos de tu tablero.'))
      .finally(() => setCargando(false))

    api.get('/ejecutor/dashboard/novedades').then(r => setNov(r.data)).catch(() => setNov(null))
  }, [])

  useEffect(() => { cargarDatos() }, [cargarDatos])
  useCambios(cargarDatos, { esperaMs: 1500 })

  const ir = (ruta) => navigate(ruta)
  const irPestana = (id) => irAPestana(setPestana, id)
  const verLista = (estado) => ir(estado ? `${config.ruta}?estado=${estado}` : config.ruta)

  /* ── Series ── */
  const estados = useSemaforoSerie()

  const { agenda, confirmacion, futuras } = useMemo(() => resumirAgenda([...(porHacer ?? []), ...(historial ?? [])]), [porHacer, historial])
  const horasHoy = useMemo(() => porHoraHoy(porHacer), [porHacer])
  const paraHoy = suma(horasHoy, config.serie)

  // Entrega al cliente de lo que tiene por hacer, según el estado de tiempo del SERVICIO (días hábiles, el mismo que ve el cliente).
  const entregas = useMemo(() => {
    const b = { Vencidas: 0, "Por vencer": 0, "A tiempo": 0, "En pausa": 0 }
    ;(porHacer ?? []).forEach(a => {
      const e = a.tiempo?.estadoTiempo
      if (e === "VENCIDA") b.Vencidas++; else if (e === "POR_VENCER") b["Por vencer"]++; else if (e === "A_TIEMPO") b["A tiempo"]++; else if (e === "EN_PAUSA") b["En pausa"]++
    })
    const colores = [C.rojo, C.ambar, C.verde, C.gris]
    return Object.entries(b).map(([name, value], i) => ({ name, value, color: colores[i] }))
  }, [porHacer])
  const vencidas = entregas[0].value

  const porTipo = useMemo(() => contarPor(porHacer, a => a.subproceso), [porHacer])

  // Rendimiento: completadas y aprobadas por semana (8), tiempo promedio de respuesta, este mes
  const rendimiento = useMemo(() => {
    const lunes = new Date(); lunes.setHours(0, 0, 0, 0); lunes.setDate(lunes.getDate() - ((lunes.getDay() + 6) % 7))
    const semanas = Array.from({ length: 8 }, (_, i) => { const d = new Date(lunes); d.setDate(d.getDate() - 7 * (7 - i)); return d })
    const filas = semanas.map(d => ({ inicio: iso(d), semana: semanaCorta(iso(d)), Completadas: 0, Aprobadas: 0 }))
    const indice = (fecha) => {
      if (!fecha) return -1
      const t = new Date(fecha).getTime()
      return semanas.findIndex((d, i) => t >= d.getTime() && (i === 7 || t < semanas[i + 1].getTime()))
    }
    const horas = []
    const hoy = new Date()
    let esteMes = 0
    ;(historial ?? []).forEach(a => {
      const i = indice(a.fechaCompletado); if (i >= 0) filas[i].Completadas++
      const j = indice(a.fechaPublicado); if (j >= 0) filas[j].Aprobadas++
      if (a.fechaAsignacion && a.fechaCompletado) {
        const h = (new Date(a.fechaCompletado) - new Date(a.fechaAsignacion)) / 3600000
        if (h >= 0) horas.push(h)
      }
      if (a.fechaCompletado) {
        const f = new Date(a.fechaCompletado)
        if (f.getFullYear() === hoy.getFullYear() && f.getMonth() === hoy.getMonth()) esteMes++
      }
    })
    const promedio = horas.length ? horas.reduce((x, y) => x + y, 0) / horas.length : null
    return { filas, promedio, esteMes, total: (historial ?? []).length }
  }, [historial])
  const textoPromedio = rendimiento.promedio == null ? '—' : rendimiento.promedio < 24 ? `${Math.round(rendimiento.promedio)} h` : `${(rendimiento.promedio / 24).toFixed(1)} d`

  const PESTANAS = [
    { id: 'hoy', label: 'Hoy', badge: paraHoy || null },
    { id: 'agenda', label: 'Agenda' },
    { id: 'rendimiento', label: 'Rendimiento' },
    { id: 'novedades', label: 'Novedades', badge: nov?.abiertas, alerta: (nov?.abiertasMas48h ?? 0) > 0 },
  ]

  return (
    <div className="mx-auto max-w-7xl">
      <EncabezadoPagina
        seccion={`Panel ${prefijo === 'analista' ? 'del analista' : prefijo === 'poligrafista' ? 'del poligrafista' : 'del visitador'}`}
        titulo={`${saludo()}, ${usuario?.nombre?.trim() || ''}`}
        descripcion={<span className="inline-block first-letter:uppercase">{fechaLarga()}</span>}
        icono={Icono.panel('h-6 w-6')}
        acciones={<button type="button" onClick={() => verLista()} className={botonPrimario}>{Icono.lista('h-4 w-4')} {config.boton}</button>}
      />

      <AlertaError mensaje={error} />

      {cargando ? <EsqueletoPagina indicadores={5} bloques={2} /> : (
        <div className="space-y-5">

          {/* ── Lo urgente ── */}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            <Atencion titulo="En ejecución" valor={conteos?.asignados ?? 0} detalle="Asignados a ti" vacio="Nada pendiente"
              icono={Icono.lista('h-4 w-4')} tono="primario" onClick={() => verLista('EN_EJECUCION')} />
            <Atencion titulo="Para hoy" valor={paraHoy} detalle="Agendados para hoy" vacio="Nada agendado hoy"
              icono={Icono.calendario('h-4 w-4')} tono="primario" onClick={() => irPestana('hoy')} />
            <Atencion titulo="Entrega vencida" valor={vencidas} detalle="Ya pasó la fecha de entrega" vacio="Ninguna vencida"
              icono={Icono.reloj('h-4 w-4')} tono="rojo" onClick={() => irPestana('hoy')} />
            <Atencion titulo="Finalizado" valor={conteos?.completados ?? 0} detalle="Esperan publicación" vacio="Nada en revisión"
              icono={Icono.sello('h-4 w-4')} tono="ambar" onClick={() => verLista('FINALIZADO')} />
            <Atencion titulo="Mis novedades abiertas" valor={nov?.abiertas ?? 0}
              detalle={(nov?.abiertasMas48h ?? 0) > 0 ? `${nov.abiertasMas48h} con más de 48 h` : 'Esperan respuesta'}
              vacio="Ninguna abierta" icono={Icono.alerta('h-4 w-4')} tono={(nov?.abiertasMas48h ?? 0) > 0 ? 'rojo' : 'ambar'}
              onClick={() => irPestana('novedades')} />
          </div>

          {/* ── Estado de su trabajo (barra lineal) ── */}
          <BarraEstados titulo={config.estado} descripcion="Todo lo que tienes registrado, por estado · clic para ver"
            estados={estados} destacado={{ valor: estados.filter(e => ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO'].includes(e.clave)).reduce((s, e) => s + e.value, 0), etiqueta: 'en curso' }}
            onClick={(x) => verLista(x.clave)} />

          <Pestanas pestanas={PESTANAS} activa={pestana} onCambiar={setPestana} />

          {pestana === 'hoy' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
              <Grafica titulo="Hoy, hora por hora" descripcion={`${paraHoy} ${paraHoy === 1 ? config.unidad : `${config.unidad}s`} agendad${paraHoy === 1 ? 'o' : 'os'} de 6 a. m. a 7 p. m.`}>
                {paraHoy === 0 ? <SinDatos texto="No tienes nada agendado para hoy" /> : <GraficaHoras datos={horasHoy} series={[config.serie]} />}
              </Grafica>
              <Grafica titulo="Entregas de lo que tienes por hacer" descripcion="Qué tan cerca está la fecha de entrega al cliente"
                accion={(conteos?.asignados ?? 0) > 0 && <Enlace onClick={() => verLista('EN_EJECUCION')}>Ver</Enlace>}>
                {suma(entregas) === 0 ? <SinDatos texto="No tienes pendientes" /> : (
                  <ResponsiveContainer width="100%" height={220}>
                    <BarChart data={entregas} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
                      <CartesianGrid vertical={false} stroke="#f3f4f6" />
                      <XAxis dataKey="name" tick={eje} axisLine={false} tickLine={false} interval={0} />
                      <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
                      <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }} formatter={(v) => [v, 'Por hacer']} />
                      <Bar dataKey="value" radius={[6, 6, 0, 0]} maxBarSize={48}>
                        {entregas.map(x => <Cell key={x.name} fill={x.color} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </Grafica>
            </div>
          )}

          {pestana === 'agenda' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
              <Grafica className="xl:col-span-3" titulo="Agenda" descripcion="Semana pasada y próximos 14 días"
                accion={<Enlace onClick={() => verLista('EN_EJECUCION')}>{config.boton}</Enlace>}>
                {agenda.every(d => d[config.serie] === 0)
                  ? <SinDatos texto="No tienes nada agendado en estas fechas" /> : <GraficaAgenda agenda={agenda} series={[config.serie]} />}
              </Grafica>
              {config.citas ? (
                <Grafica className="xl:col-span-2" titulo="Confirmación de asistencia" descripcion="Tus citas de hoy en adelante">
                  <Dona datos={confirmacion} total={futuras.length} etiquetaTotal="citas" vacio="No tienes citas por venir" />
                </Grafica>
              ) : (
                <Grafica className="xl:col-span-2" titulo="Por hacer, por tipo" descripcion="Qué subprocesos tienes pendientes">
                  <BarrasHorizontales datos={porTipo} color={C.indigo} unidad="Por hacer" vacio="No tienes pendientes" />
                </Grafica>
              )}
            </div>
          )}

          {pestana === 'rendimiento' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
              <Grafica className="xl:col-span-3" titulo="Completadas y aprobadas" descripcion="Por semana · últimas 8 semanas">
                {rendimiento.total === 0 ? <SinDatos texto={`Cuando completes tu primer ${config.unidad} verás aquí tu ritmo`} /> : (
                  <ResponsiveContainer width="100%" height={220}>
                    <BarChart data={rendimiento.filas} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
                      <CartesianGrid vertical={false} stroke="#f3f4f6" />
                      <XAxis dataKey="semana" tick={eje} axisLine={false} tickLine={false} />
                      <YAxis allowDecimals={false} tick={eje} axisLine={false} tickLine={false} />
                      <Tooltip contentStyle={tooltipEstilo} cursor={{ fill: '#f9fafb' }} labelFormatter={(l) => `Semana del ${l}`} />
                      <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="Completadas" fill={C.violeta} radius={[5, 5, 0, 0]} maxBarSize={18} />
                      <Bar dataKey="Aprobadas" fill={C.verde} radius={[5, 5, 0, 0]} maxBarSize={18} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </Grafica>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 xl:col-span-2 xl:grid-cols-1">
                {[
                  ['Tiempo promedio de respuesta', textoPromedio, 'Desde que te lo asignan hasta que lo completas'],
                  ['Completadas este mes', rendimiento.esteMes, new Date().toLocaleDateString('es-CO', { month: 'long', year: 'numeric' })],
                  ['Publicadas', conteos?.publicados ?? 0, `De ${rendimiento.total} completadas`],
                ].map(([t, v, d]) => (
                  <div key={t} className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
                    <p className="text-xs font-medium text-gray-500">{t}</p>
                    <p className="mt-1 text-2xl font-bold tabular-nums text-gray-900">{v}</p>
                    <p className="mt-0.5 text-[11px] text-gray-400 first-letter:uppercase">{d}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {pestana === 'novedades' && (
            <SeccionNovedades nov={nov} onAbrir={(n) => ir(`/${prefijo}/formulario/${n.idServicioSubproceso}`)}
              descripcion="Las novedades que tú registraste en tus subprocesos"
              tituloEspera="Esperando respuesta" descripcionEspera="Tus novedades abiertas más antiguas · clic para ver el seguimiento"
              etiquetaRespuesta="Te responden en promedio (30 días)" />
          )}
        </div>
      )}
    </div>
  )
}
