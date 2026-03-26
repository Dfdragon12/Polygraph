import { Routes, Route, Navigate } from 'react-router-dom'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<div className="flex items-center justify-center min-h-screen"><h1 className="text-2xl font-bold text-primary-700">Hola Carlos Abondano :D</h1></div>} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

export default App
