package com.polygraph.erp.modules.tiemporeal;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.SoporteCliente;
import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.servicios.entity.ReversionSolicitud;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.NovedadSeguimiento;
import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.entity.SugerenciaDisponibilidad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.extern.slf4j.Slf4j;

/** Escucha los cambios de las entidades que se ven en pantalla (servicio, subprocesos, novedades y
 *  su seguimiento, documentos y hoja de vida del candidato, sugerencias de horario, link) y anota
 *  QUÉ cambió en RegistroCambios. Solo lee ids (el id de una relación LAZY se lee sin cargarla):
 *  nunca consulta la base aquí, porque corre en medio del flush de Hibernate.
 *
 *  Nunca debe romper un guardado: cualquier error se registra y se ignora. */
@Slf4j
public class CambiosEntityListener {

    @PostPersist
    @PostUpdate
    @PostRemove
    public void alCambiar(Object entidad) {
        try {
            anotar(entidad);
        } catch (Exception e) {
            log.debug("No se pudo anotar el cambio de {} para tiempo real: {}", entidad.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static void anotar(Object entidad) {
        if (entidad instanceof Servicio s) {
            RegistroCambios.servicio(s.getIdServicio());
        } else if (entidad instanceof ServicioSubproceso sp) {
            if (sp.getServicio() != null) RegistroCambios.servicio(sp.getServicio().getIdServicio());
            else RegistroCambios.subproceso(sp.getId());
        } else if (entidad instanceof NovedadSubproceso n) {
            if (n.getServicioSubproceso() != null) RegistroCambios.subproceso(n.getServicioSubproceso().getId());
        } else if (entidad instanceof NovedadSeguimiento sg) {
            if (sg.getNovedad() != null) RegistroCambios.novedad(sg.getNovedad().getId());
        } else if (entidad instanceof SugerenciaDisponibilidad sd) {
            if (sd.getServicioSubproceso() != null) RegistroCambios.subproceso(sd.getServicioSubproceso().getId());
        } else if (entidad instanceof DocumentoEvaluado d) {
            if (d.getHojaVidaEvaluado() != null) RegistroCambios.hojaVida(d.getHojaVidaEvaluado().getId());
        } else if (entidad instanceof HojaVidaEvaluado hv) {
            if (hv.getCandidato() != null) RegistroCambios.candidato(hv.getCandidato().getIdCandidato());
        } else if (entidad instanceof LinkCandidato l) {
            if (l.getServicio() != null) RegistroCambios.servicio(l.getServicio().getIdServicio());
        } else if (entidad instanceof SoporteCliente) {
            RegistroCambios.admin("documentos");
        } else if (entidad instanceof ReversionSolicitud) {
            RegistroCambios.admin("reversiones");
        } else if (entidad instanceof PagoWompi || entidad instanceof FacturaPospago) {
            RegistroCambios.admin("pagos");
        } else if (entidad instanceof Cliente) {
            RegistroCambios.admin("clientes");
        } else if (entidad instanceof Usuario) {
            RegistroCambios.admin("usuarios");
        } else if (entidad instanceof Ciudad) {
            RegistroCambios.admin("ciudades");
        } else if (entidad instanceof TrabajoAsincrono) {
            RegistroCambios.admin("trabajos");
        } else if (entidad instanceof CalificacionRiesgo) {
            RegistroCambios.admin("riesgo");
        }
    }
}
