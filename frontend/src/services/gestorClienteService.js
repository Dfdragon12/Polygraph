import api from './api'

// El gestor le crea un usuario ANALISTA_CLIENTE adicional a uno de sus clientes — para cuando el
// cliente ya llegó al tope de autoservicio (ver perfilService.crearUsuario) y se lo pide por el
// chat. Sin tope: es una excepción puntual que decide el gestor.
const gestorClienteService = {
  listarUsuarios: (idCliente) => api.get(`/gestor/clientes/${idCliente}/usuarios`).then(r => r.data),
  crearUsuario: (idCliente, payload) => api.post(`/gestor/clientes/${idCliente}/usuarios`, payload).then(r => r.data),
}

export default gestorClienteService
