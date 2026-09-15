import { useEffect, useRef, useState } from 'react'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID

let scriptCargando = null
function cargarScriptGoogle() {
  if (window.google?.accounts?.id) return Promise.resolve()
  if (!scriptCargando) {
    scriptCargando = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      script.defer = true
      script.onload = resolve
      script.onerror = reject
      document.head.appendChild(script)
    })
  }
  return scriptCargando
}

// Logo oficial de Google ("G" de 4 colores) — se usa tanto en el botón real como en el de
// vista previa, para que ambos se vean idénticos mientras no está configurado el Client ID.
function LogoGoogle() {
  return (
    <svg className="w-[18px] h-[18px]" viewBox="0 0 48 48">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59A14.5 14.5 0 019.5 24c0-1.59.27-3.13.76-4.59l-7.98-6.19A23.94 23.94 0 000 24c0 3.86.92 7.51 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
      <path fill="none" d="M0 0h48v48H0z" />
    </svg>
  )
}

const CLASE_BOTON_PREVIA = 'inline-flex items-center justify-center gap-2.5 w-[320px] h-11 rounded-full border border-gray-300 bg-white text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors'

/** Botón "Continuar con Google" (Google Identity Services) — dibuja el botón oficial de Google
 *  (así se ven bien y cumplen sus lineamientos de marca) dentro de un contenedor del ancho que
 *  uno le dé. Sin VITE_GOOGLE_CLIENT_ID configurado, muestra el mismo botón pero de vista previa
 *  — se ve igual, pero al hacer click avisa que todavía no está activo en vez de intentar algo
 *  que de todas formas fallaría en silencio. */
export default function GoogleSignInButton({ onCredential, texto = 'signup_with' }) {
  const contenedorRef = useRef(null)
  const [listo, setListo] = useState(false)
  const [error, setError] = useState(false)
  const [avisoPrevia, setAvisoPrevia] = useState(false)

  useEffect(() => {
    if (!CLIENT_ID) return
    let cancelado = false
    cargarScriptGoogle()
      .then(() => {
        if (cancelado || !contenedorRef.current) return
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: (respuesta) => onCredential(respuesta.credential),
        })
        window.google.accounts.id.renderButton(contenedorRef.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          shape: 'pill',
          text: texto,
          logo_alignment: 'left',
          width: 320,
        })
        setListo(true)
      })
      .catch(() => setError(true))
    return () => { cancelado = true }
  }, [onCredential, texto])

  if (!CLIENT_ID || error) {
    return (
      <div className="flex flex-col items-center gap-2">
        <button type="button" onClick={() => setAvisoPrevia(true)} className={CLASE_BOTON_PREVIA}>
          <LogoGoogle />
          Continuar con Google
        </button>
        {avisoPrevia && (
          <p className="text-xs text-gray-400 text-center max-w-[320px]">
            Todavía no está activo — muy pronto vas a poder registrarte con tu cuenta de Google.
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="flex justify-center">
      <div ref={contenedorRef} className={listo ? '' : 'h-11 w-[320px] rounded-full bg-gray-100 animate-pulse'} />
    </div>
  )
}
