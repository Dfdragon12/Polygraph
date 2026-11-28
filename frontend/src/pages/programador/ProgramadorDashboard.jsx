import { useSemaforoSerie } from '../../hooks/useSemaforoSerie'
import { useState, useEffect, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import api from '../../services/api'
import asignacionService from '../../services/asignacionService'
import { useCambios } from '../../hooks/useCambios'
import { EncabezadoPagina, EsqueletoPagina, AlertaError, botonPrimario } from '../../components/admin/PaginaAdmin'
import {
  C, Icono, saludo, fechaLarga, rangoAgenda, resumirAgenda, contarPor, porHoraHoy,
  Atencion, Grafica, Enlace, SinDatos, Cargando, Dona, BarrasHorizontales, BarraEstados, GraficaAgenda, GraficaHoras,
  SeccionNovedades, Pestanas, usePestana, irAPestana,
} from '../../components/tablero/Tablero'

// Tablero del programador, igual que el del gestor: arriba lo urgente (novedades, por agendar,
// citas de hoy, por confirmar, por reprogramar) y la barra de estado de sus poligrafías y visitas;
// debajo, en pestañas, el día de hoy, la agenda, lo que falta por agendar y las novedades de sus
// subprocesos (él también las resuelve). Todo viene acotado a lo suyo por el backend
// (programador responsable).

export default function ProgramadorDashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
  const [conteos, setConteos] = useState(null)
  const [citas, setCitas] = useState(null)
  const [porAgendar, setPorAgendar] = useState(null)
  const [nov, setNov] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [pestana, setPestana] = usePestana('tablero-programador-pestana', 'hoy')

  // Carga todo el tablero; también se llama sola cuando algo cambia (tiempo real), sin spinner.
  const cargarDatos = useCallback(() => {
    Promise.all([
      asignacionService.conteo(),
      asignacionService.calendario(...rangoAgenda()),
    ])
      .then(([c, agenda]) => { setConteos(c); setCitas(agenda ?? []) })
      .catch(() => { setError('No se pudo cargar el tablero. Intenta recargar la página.'); setCitas([]) })
      .finally(() => setCargando(false))

    api.get('/programador/dashboard/novedades').then(r => setNov(r.data)).catch(() => setNov(null))

    asignacionService.listar({ estado: 'PENDIENTE', size: 200 })
      .then(r => setPorAgendar(r.content ?? []))
      .catch(() => setPorAgendar([]))
  }, [])

  useEffect(() => { cargarDatos() }, [cargarDatos])
  useCambios(cargarDatos, { esperaMs: 1500 })

  const ir = (ruta) => navigate(ruta)
  const irPestana = (id) => irAPestana(setPestana, id)
  const agendaCronograma = () => ir('/programador/agenda?seccion=cronograma')

  const { futuras, agenda, confirmacion, citasHoy } = useMemo(() => resumirAgenda(citas), [citas])
  const conf = Object.fromEntries(confirmacion.map(x => [x.clave, x.value]))
  const porConfirmar = (conf.SIN_LLAMAR ?? 0) + (conf.NO_CONTESTA ?? 0)

  const estados = useSemaforoSerie()

  const horasHoy = useMemo(() => porHoraHoy(futuras), [futuras])
  const porCiudad = useMemo(() => contarPor(porAgendar, a => a.nombreCiudad ?? 'Sin ciudad'), [porAgendar])
  const porTipo = useMemo(() => contarPor(porAgendar, a => a.subproceso), [porAgendar])
  const porProfesional = useMemo(() => contarPor(futuras, c => c.nombreAsignado ?? 'Sin profesional'), [futuras])

  const PESTANAS = [
    { id: 'hoy', label: 'Hoy', badge: citasHoy || null },
    { id: 'agenda', label: 'Agenda', badge: futuras.length || null },
    { id: 'porAgendar', label: 'Programando', badge: conteos?.pendientes },
    { id: 'novedades', label: 'Novedades', badge: nov?.abiertas, alerta: (nov?.abiertasMas48h ?? 0) > 0 },
  ]

  return (
    <div className="mx-auto max-w-7xl">
      <EncabezadoPagina
        seccion="Panel del programador"
        titulo={`${saludo()}, ${usuario?.nombre?.trim() || 'programador'}`}
        descripcion={<span className="inline-block first-letter:uppercase">{fechaLarga()}</span>}
        icono={Icono.panel('h-6 w-6')}
        acciones={<button type="button" onClick={() => ir('/programador/agenda')} className={botonPrimario}>{Icono.calendario('h-4 w-4')} Agenda</button>}
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
            <Atencion titulo="Programando" valor={conteos?.pendientes ?? 0} detalle="Sin fecha todavía" vacio="Todo agendado"
              icono={Icono.persona('h-4 w-4')} tono="ambar" onClick={() => irPestana('porAgendar')} />
            <Atencion titulo="Citas hoy" valor={citasHoy} detalle="Programadas para hoy" vacio="Sin citas hoy"
              icono={Icono.calendario('h-4 w-4')} tono="primario" onClick={() => irPestana('hoy')} />
            <Atencion titulo="Por confirmar" valor={porConfirmar} detalle="Sin llamar o no contestan" vacio="Todas confirmadas"
              icono={Icono.telefono('h-4 w-4')} tono="ambar" onClick={() => irPestana('hoy')} />
            <Atencion titulo="Por reprogramar" valor={conf.REPROGRAMAR ?? 0} detalle="Pidieron otra fecha" vacio="Ninguna"
              icono={Icono.repetir('h-4 w-4')} tono="rojo" onClick={agendaCronograma} />
          </div>

          {/* ── Estado de lo suyo (barra lineal) ── */}
          <BarraEstados titulo="Mis poligrafías y visitas" descripcion="Todo lo que coordinas, por estado · clic para ir a la agenda"
            estados={estados} destacado={{ valor: estados.filter(e => ['PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO'].includes(e.clave)).reduce((s, e) => s + e.value, 0), etiqueta: 'en curso' }}
            onClick={() => ir('/programador/agenda')} />

          <Pestanas pestanas={PESTANAS} activa={pestana} onCambiar={setPestana} />

          {pestana === 'hoy' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
              <Grafica className="xl:col-span-3" titulo="Hoy, hora por hora" descripcion={`${citasHoy} cita${citasHoy !== 1 ? 's' : ''} de 6 a. m. a 7 p. m.`}
                accion={<Enlace onClick={agendaCronograma}>Cronograma</Enlace>}>
                {citas === null ? <Cargando /> : citasHoy === 0
                  ? <SinDatos texto="No hay citas para hoy" /> : <GraficaHoras datos={horasHoy} series={['Poligrafía', 'Visita']} />}
              </Grafica>
              <Grafica className="xl:col-span-2" titulo="Confirmación de asistencia" descripcion="Citas de hoy a 14 días">
                {citas === null ? <Cargando /> : <Dona datos={confirmacion} total={futuras.length} etiquetaTotal="citas"
                  vacio="No hay citas por venir" onClick={agendaCronograma} />}
              </Grafica>
            </div>
          )}

          {pestana === 'agenda' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
              <Grafica className="xl:col-span-3" titulo="Agenda" descripcion={`Semana pasada y próximos 14 días · ${futuras.length} ${futuras.length === 1 ? 'cita' : 'citas'} por venir`}
                accion={<Enlace onClick={agendaCronograma}>Abrir agenda</Enlace>}>
                {citas === null ? <Cargando /> : agenda.every(d => d.Poligrafía + d.Visita === 0)
                  ? <SinDatos texto="No hay citas en estas fechas" /> : <GraficaAgenda agenda={agenda} series={['Poligrafía', 'Visita']} />}
              </Grafica>
              <Grafica className="xl:col-span-2" titulo="Carga por profesional" descripcion="Citas por venir de cada poligrafista y visitador">
                <BarrasHorizontales datos={porProfesional} color={C.indigo} unidad="Citas por venir" vacio="No hay citas por venir" />
              </Grafica>
            </div>
          )}

          {pestana === 'porAgendar' && (
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
              <Grafica titulo="Por ciudad" descripcion="Dónde están las citas que faltan por agendar"
                accion={(porAgendar?.length ?? 0) > 0 && <Enlace onClick={() => ir('/programador/agenda')}>Agendar</Enlace>}>
                {porAgendar === null ? <Cargando />
                  : <BarrasHorizontales datos={porCiudad} color={C.ambar} unidad="Por agendar" vacio="Todo está agendado" />}
              </Grafica>
              <Grafica titulo="Por tipo" descripcion="Qué subprocesos esperan fecha">
                {porAgendar === null ? <Cargando />
                  : <BarrasHorizontales datos={porTipo} color={C.naranja} unidad="Por agendar" vacio="Todo está agendado" />}
              </Grafica>
            </div>
          )}

          {pestana === 'novedades' && (
            <SeccionNovedades nov={nov} onAbrir={(n) => ir(`/programador/formulario/${n.idServicioSubproceso}`)}
              descripcion="Lo que reportan poligrafistas y visitadores en los subprocesos que coordinas" />
          )}
        </div>
      )}
    </div>
  )
}
