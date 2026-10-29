import { useEffect, useState } from 'react'
import { canalUsuario } from '../services/tiempoReal'

/** ¿La conexión de avisos en tiempo real está viva? Para mostrar "En vivo" / "Reconectando…". */
export function useConexionVivo() {
  const [conectado, setConectado] = useState(() => canalUsuario.estaConectado())
  useEffect(() => canalUsuario.alCambiarEstado(setConectado), [])
  return conectado
}
