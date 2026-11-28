// PF-09 · Cola local (IndexedDB) de lo que el proveedor diligenció sin señal. Una entrada por clave: el formulario se guarda
// completo en cada cambio (el servidor reemplaza todo), así que basta con conservar la ÚLTIMA versión; no hay nada que reconciliar.
// Si el navegador no ofrece IndexedDB (modo privado antiguo) se usa la memoria de la pestaña: mejor eso que perder el dato.

const BD = 'polygraph-proveedor'
const ALMACEN = 'pendientes'
const enMemoria = new Map()

function abrir() {
  return new Promise((resolver, rechazar) => {
    if (typeof indexedDB === 'undefined') return rechazar(new Error('sin IndexedDB'))
    const pedido = indexedDB.open(BD, 1)
    pedido.onupgradeneeded = () => pedido.result.createObjectStore(ALMACEN, { keyPath: 'clave' })
    pedido.onsuccess = () => resolver(pedido.result)
    pedido.onerror = () => rechazar(pedido.error)
  })
}

async function operar(modo, fn) {
  const bd = await abrir()
  try {
    return await new Promise((resolver, rechazar) => {
      const tx = bd.transaction(ALMACEN, modo)
      const almacen = tx.objectStore(ALMACEN)
      let resultado
      const pedido = fn(almacen)
      if (pedido) pedido.onsuccess = () => { resultado = pedido.result }
      tx.oncomplete = () => resolver(resultado)
      tx.onerror = () => rechazar(tx.error)
      tx.onabort = () => rechazar(tx.error)
    })
  } finally {
    bd.close()
  }
}

const colaOffline = {
  async poner(clave, valor) {
    const entrada = { ...valor, clave }
    try { await operar('readwrite', a => a.put(entrada)) } catch { enMemoria.set(clave, entrada) }
  },
  async leer(clave) {
    try { return (await operar('readonly', a => a.get(clave))) ?? enMemoria.get(clave) ?? null } catch { return enMemoria.get(clave) ?? null }
  },
  // Solo se borra si nadie escribió una versión más nueva mientras se enviaba la anterior.
  async borrarSiIgual(clave, ts) {
    const actual = await this.leer(clave)
    if (!actual || actual.ts !== ts) return
    try { await operar('readwrite', a => a.delete(clave)) } catch { /* queda en memoria */ }
    enMemoria.delete(clave)
  },
}

export default colaOffline
