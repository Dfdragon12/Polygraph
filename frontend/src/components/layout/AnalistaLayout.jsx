import InternalLayout from './InternalLayout'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/analista/dashboard',    etiqueta: 'Dashboard',      icono: '📊' },
    ],
  },
  {
    titulo: 'Mi Trabajo',
    items: [
      { ruta: '/analista/asignaciones', etiqueta: 'Mis Asignaciones', icono: '📌' },
      { ruta: '/analista/validaciones', etiqueta: 'Validaciones',     icono: '✅' },
      { ruta: '/analista/informes',     etiqueta: 'Informes',         icono: '📄' },
    ],
  },
]

export default function AnalistaLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Analista Interno" />
}
