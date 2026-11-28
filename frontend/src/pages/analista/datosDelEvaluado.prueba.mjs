// Prueba de AC-08: node src/pages/analista/datosDelEvaluado.prueba.mjs
import { filasDeIdentidad, datoDelLink } from './datosDelEvaluado.js'
const ok = (c, m) => { console.log(c ? 'OK  ' : 'FALLA', m); if (!c) process.exitCode = 1 }
const link = [
  { clave: 'NOMBRE_COMPLETO', valor: 'Laura Ejemplo Pérez' }, { clave: 'TIPO_DOCUMENTO', valor: 'CC' }, { clave: 'CEDULA', valor: '1056235689' },
  { clave: 'FECHA_EXPEDICION', valor: '20/05/2006' }, { clave: 'CIUDAD_EXPEDICION', valor: 'Bogotá' },
  { clave: 'FECHA_NACIMIENTO', valor: '12/04/1988' }, { clave: 'LUGAR_NACIMIENTO', valor: 'Bogotá' },
]
const f = Object.fromEntries(filasDeIdentidad(link))
ok(f['Fecha de expedición'] === '20/05/2006' && f['Lugar de expedición'] === 'Bogotá', 'AC-08: expedición del link')
ok(f['Fecha de nacimiento'] === '12/04/1988' && f['Lugar de nacimiento'] === 'Bogotá', 'AC-08: nacimiento del link')
ok(f['N.º de documento'] === 'CC 1056235689', 'AC-08: documento con su tipo')
const g = Object.fromEntries(filasDeIdentidad([{ clave: 'CIUDAD_NACIMIENTO', valor: 'Cali' }], 'Medellín'))
ok(g['Lugar de nacimiento'] === 'Cali' && g['Lugar de expedición'] === 'Medellín', 'AC-08: respaldo por la ciudad de nacimiento y por la plantilla')
ok(datoDelLink(undefined, 'CEDULA') === '', 'AC-08: sin datos no falla')
