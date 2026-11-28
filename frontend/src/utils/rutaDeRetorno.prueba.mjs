// node src/utils/rutaDeRetorno.prueba.mjs — PC-06: la ruta de retorno solo vuelve para la misma persona y solo a rutas internas.
import assert from 'node:assert/strict'

const memoria = new Map()
globalThis.sessionStorage = { getItem: (k) => memoria.get(k) ?? null, setItem: (k, v) => memoria.set(k, String(v)), removeItem: (k) => memoria.delete(k) }
const { esRutaDeRetorno, recordarRuta, rutaParaVolver, olvidarRuta, marcarCierreAutomatico, hayCierreAutomatico, olvidarCierreAutomatico } = await import('./rutaDeRetorno.js')

// Rutas válidas e inválidas
assert.equal(esRutaDeRetorno('/gestor/solicitudes?abierto=52&estado=FINALIZADO'), true)
assert.equal(esRutaDeRetorno('/analista/formulario/9#novedades'), true)
for (const mala of ['//evil.example', 'https://evil.example', '/' + String.fromCharCode(92) + 'evil', '/login', '/login?motivo=inactividad', '/legal/terminos', '/evaluado/link/abc', '/proveedor/abc', '', null, undefined, 'gestor/solicitudes'])
  assert.equal(esRutaDeRetorno(mala), false, `no debe volver a: ${mala}`)

// La misma persona vuelve a donde estaba (con filtros y fila abierta), una sola vez
assert.equal(recordarRuta(7, '/gestor/solicitudes?abierto=52', 1000), true)
assert.equal(rutaParaVolver(7, 2000), '/gestor/solicitudes?abierto=52')
assert.equal(rutaParaVolver(7, 2000), null, 'se consume')

// Otra persona en el mismo navegador NO hereda la pantalla de la anterior
recordarRuta(7, '/gestor/solicitudes', 1000)
assert.equal(rutaParaVolver(8, 2000), null)
assert.equal(rutaParaVolver(7, 2000), null, 'y queda olvidada')

// Pasadas 12 horas ya no vale
recordarRuta(7, '/gestor/solicitudes', 0)
assert.equal(rutaParaVolver(7, 13 * 60 * 60 * 1000), null)

// Sin persona o con una ruta no válida no se guarda nada
assert.equal(recordarRuta(null, '/gestor/solicitudes'), false)
assert.equal(recordarRuta(7, '/login'), false)
olvidarRuta()
assert.equal(rutaParaVolver(7), null)

// La marca del cierre automático se lee y se borra
assert.equal(hayCierreAutomatico(), false)
marcarCierreAutomatico()
assert.equal(hayCierreAutomatico(), true)
olvidarCierreAutomatico()
assert.equal(hayCierreAutomatico(), false)

console.log('rutaDeRetorno: todo bien')
