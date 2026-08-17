package com.polygraph.erp.modules.enlaces.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.enlaces.dto.EnlaceExternoRequest;
import com.polygraph.erp.modules.enlaces.dto.EnlaceExternoResponse;
import com.polygraph.erp.modules.enlaces.entity.EnlaceExterno;
import com.polygraph.erp.modules.enlaces.repository.EnlaceExternoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EnlaceExternoService {

    private final EnlaceExternoRepository repositorio;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<EnlaceExternoResponse> listarActivos() {
        return repositorio.findByActivoTrueOrderByCategoriaAscNombreEntidadAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EnlaceExternoResponse> listarTodos() {
        return repositorio.findAllByOrderByCategoriaAscNombreEntidadAsc()
                .stream().map(this::toResponse).toList();
    }

    public EnlaceExternoResponse crear(EnlaceExternoRequest req, String emailActual) {
        Usuario autor = usuarioRepository.findByEmail(emailActual).orElse(null);
        EnlaceExterno enlace = EnlaceExterno.builder()
                .categoria(req.categoria())
                .nombreEntidad(req.nombreEntidad())
                .url(req.url())
                .correoContacto(req.correoContacto())
                .telefonoContacto(req.telefonoContacto())
                .ciudad(req.ciudad())
                .requiereLogin(req.requiereLogin() != null ? req.requiereLogin() : false)
                .notas(req.notas())
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .creadoPor(autor)
                .build();
        EnlaceExterno guardado = repositorio.save(enlace);
        log.info("Enlace externo creado: {} por {}", guardado.getNombreEntidad(), emailActual);
        String autorNombre = autor != null ? autor.getNombre() + (autor.getApellido() != null ? " " + autor.getApellido() : "") : emailActual;
        notificacionService.crearParaAdmins("ENLACES", "Enlace externo agregado",
                autorNombre + " agregó \"" + guardado.getNombreEntidad() + "\" en Referencias externas");
        return toResponse(guardado);
    }

    public EnlaceExternoResponse actualizar(Integer id, EnlaceExternoRequest req) {
        EnlaceExterno enlace = buscarOFallar(id);
        enlace.setCategoria(req.categoria());
        enlace.setNombreEntidad(req.nombreEntidad());
        enlace.setUrl(req.url());
        enlace.setCorreoContacto(req.correoContacto());
        enlace.setTelefonoContacto(req.telefonoContacto());
        enlace.setCiudad(req.ciudad());
        if (req.requiereLogin() != null) enlace.setRequiereLogin(req.requiereLogin());
        enlace.setNotas(req.notas());
        log.info("Enlace externo actualizado: {}", enlace.getNombreEntidad());
        notificacionService.crearParaAdmins("ENLACES", "Enlace externo modificado",
                "Se modificó \"" + enlace.getNombreEntidad() + "\" en Referencias externas");
        return toResponse(enlace);
    }

    public EnlaceExternoResponse cambiarEstado(Integer id, boolean activo) {
        EnlaceExterno enlace = buscarOFallar(id);
        enlace.setActivo(activo);
        log.info("Enlace {} {}", enlace.getNombreEntidad(), activo ? "activado" : "desactivado");
        notificacionService.crearParaAdmins("ENLACES",
                activo ? "Enlace externo activado" : "Enlace externo desactivado",
                "\"" + enlace.getNombreEntidad() + "\" fue " + (activo ? "activado" : "desactivado") + " en Referencias externas");
        return toResponse(enlace);
    }

    public void eliminar(Integer id) {
        EnlaceExterno enlace = buscarOFallar(id);
        String nombre = enlace.getNombreEntidad();
        repositorio.delete(enlace);
        log.info("Enlace externo eliminado: {}", nombre);
        notificacionService.crearParaAdmins("ENLACES", "Enlace externo eliminado",
                "\"" + nombre + "\" fue eliminado de Referencias externas");
    }

    private EnlaceExterno buscarOFallar(Integer id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ApiException("Enlace no encontrado", HttpStatus.NOT_FOUND));
    }

    private EnlaceExternoResponse toResponse(EnlaceExterno e) {
        String creadoPorNombre = null;
        if (e.getCreadoPor() != null) {
            creadoPorNombre = e.getCreadoPor().getNombre()
                    + (e.getCreadoPor().getApellido() != null ? " " + e.getCreadoPor().getApellido() : "");
        }
        return new EnlaceExternoResponse(
                e.getIdEnlace(), e.getCategoria(), e.getNombreEntidad(),
                e.getUrl(), e.getCorreoContacto(), e.getTelefonoContacto(),
                e.getCiudad(), e.getRequiereLogin(), e.getNotas(),
                e.getActivo(), e.getFechaCreacion(), creadoPorNombre
        );
    }
}
