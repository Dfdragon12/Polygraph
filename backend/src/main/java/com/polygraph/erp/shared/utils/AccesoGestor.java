package com.polygraph.erp.shared.utils;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/** Un GESTOR solo opera sobre los servicios de SUS clientes (Cliente.idGestor). Antes solo se
 *  validaba el rol y cualquier gestor podía abrir, aprobar o devolver el subproceso de un cliente
 *  de otro gestor llamando la API directo (corregido 25-sep-2026). Los demás roles no se tocan. */
@Slf4j
public final class AccesoGestor {

    private AccesoGestor() {}

    public static boolean esGestorDelServicio(Servicio servicio, Usuario actor) {
        return servicio != null && servicio.getCliente() != null
                && Objects.equals(servicio.getCliente().getIdGestor(), actor.getIdUsuario());
    }

    /** 403 si el actor es GESTOR y el servicio no es de uno de sus clientes. */
    public static void validar(Servicio servicio, Usuario actor) {
        if (actor == null || actor.getRol() != Rol.GESTOR || esGestorDelServicio(servicio, actor)) return;
        log.warn("Acceso denegado: gestor {} intentó operar el servicio {} de un cliente que no gestiona",
                actor.getIdUsuario(), servicio != null ? servicio.getIdServicio() : null);
        throw new ApiException("Este servicio es de un cliente que no gestionas.", HttpStatus.FORBIDDEN);
    }

    public static void validar(ServicioSubproceso subproceso, Usuario actor) {
        validar(subproceso != null ? subproceso.getServicio() : null, actor);
    }
}
