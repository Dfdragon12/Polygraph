import { Link } from 'react-router-dom'
import { EMPRESA } from '../../config/empresa'
import { abrirPreferenciasCookies } from '../../utils/consentimiento'

/** Pie con los datos del negocio y enlaces legales (Ley 1480, art. 50). */
export default function PieLegal({ className = '' }) {
  const enlace = 'text-primary-700 underline hover:text-primary-800'
  return (
    <footer className={`text-xs text-gray-700 text-center space-y-1 py-4 px-4 ${className}`}>
      <nav aria-label="Información legal" className="flex flex-wrap justify-center gap-x-4 gap-y-1">
        <Link className={enlace} to="/legal/privacidad">Privacidad</Link>
        <Link className={enlace} to="/legal/terminos">Términos y condiciones</Link>
        <Link className={enlace} to="/legal/cookies">Cookies</Link>
        <Link className={enlace} to="/legal/reembolsos">Reembolsos y cancelaciones</Link>
        <button type="button" className={enlace} onClick={abrirPreferenciasCookies}>Preferencias de cookies</button>
      </nav>
      <p>
        © {new Date().getFullYear()} {EMPRESA.razonSocial} · NIT {EMPRESA.nit} · {EMPRESA.direccion}
      </p>
      <p>{EMPRESA.correo} · {EMPRESA.telefono} · {EMPRESA.horario}</p>
    </footer>
  )
}
