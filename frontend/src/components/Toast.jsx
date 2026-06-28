import { useEffect } from 'react'

export default function Toast({ mensaje, tipo = 'exito', onClose }) {
  useEffect(() => {
    const t = setTimeout(onClose, 3500)
    return () => clearTimeout(t)
  }, [onClose])

  const esExito = tipo === 'exito'

  return (
    <div className={`fixed bottom-6 right-6 z-50 flex items-center gap-3 px-5 py-3.5 rounded-xl shadow-lg text-sm font-medium animate-fade-in ${
      esExito ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
    }`}>
      <span className="text-base">{esExito ? '✓' : '✕'}</span>
      <span>{mensaje}</span>
      <button onClick={onClose} className="ml-2 opacity-70 hover:opacity-100 text-base leading-none">×</button>
    </div>
  )
}
