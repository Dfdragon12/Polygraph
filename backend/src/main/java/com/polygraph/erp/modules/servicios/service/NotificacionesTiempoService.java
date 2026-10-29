package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.SolicitudPausa;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.utils.FechasLegibles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Los avisos del tiempo de una solicitud. Se generan UNA sola vez por transición: SemaforoTiempoService llama aquí solo cuando el estado de
 * tiempo cambió de verdad (el recálculo de cada noche no repite nada) y nunca en el primer cálculo de una solicitud.
 *
 *  · pasa a POR_VENCER → al gestor del cliente y al cliente;
 *  · pasa a VENCIDA → al gestor, a los administradores y al cliente;
 *  · se reprograma → al cliente, con la fecha anterior, la nueva y el motivo;
 *  · se abre o se cierra una pausa → al gestor.
 *
 * Las fechas van como "23 de septiembre de 2026". Todo ocurre en la transacción de quien llama (si se revierte, los avisos también), y un fallo
 * al avisar nunca debe impedir calcular el tiempo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionesTiempoService {

    public static final String TIPO_POR_VENCER = "TIEMPO_POR_VENCER";
    public static final String TIPO_VENCIDA = "TIEMPO_VENCIDA";
    public static final String TIPO_PAUSA_ABIERTA = "TIEMPO_PAUSA_ABIERTA";
    public static final String TIPO_PAUSA_CERRADA = "TIEMPO_PAUSA_CERRADA";
    public static final String TIPO_REPROGRAMADA = "SOLICITUD_REPROGRAMADA";
    static final String REFERENCIA = "SERVICIO";

    /** La pausa automática de "espera la hoja de vida" no se avisa: el gestor ya recibe el aviso de la solicitud nueva y el de la hoja de vida completada. */
    static final boolean AVISAR_PAUSA_HOJA_DE_VIDA = false;

    private final NotificacionService notificaciones;
    private final UsuarioRepository usuarioRepository;

    // ───────────────────────── cambio de estado de tiempo ─────────────────────────

    public void alCambiarEstadoTiempo(Servicio s, EstadoTiempo anterior, EstadoTiempo nuevo) {
        if (anterior == null || nuevo == null || anterior == nuevo) return;      // primer cálculo o sin cambio: nada que avisar
        try {
            if (nuevo == EstadoTiempo.POR_VENCER) {
                String quien = describir(s);
                String vence = s.getDiasRestantesHabiles() != null && s.getDiasRestantesHabiles() == 0 ? "vence hoy"
                        : "vence el " + FechasLegibles.larga(s.getFechaCompromisoAjustada());
                para(gestor(s), TIPO_POR_VENCER, "Solicitud por vencer #" + s.getIdServicio(),
                        quien + " " + vence + ". Revisa que salga a tiempo.", s);
                paraCliente(s, TIPO_POR_VENCER, "Tu solicitud #" + s.getIdServicio() + " está por vencer",
                        "El estudio de " + evaluado(s) + " " + vence + " (fecha de compromiso).", s);
            } else if (nuevo == EstadoTiempo.VENCIDA) {
                String fecha = FechasLegibles.larga(s.getFechaCompromisoAjustada());
                String retraso = s.getDiasRetrasoHabiles() != null && s.getDiasRetrasoHabiles() > 0
                        ? " Lleva " + dias(s.getDiasRetrasoHabiles()) + " de retraso." : "";
                String interno = describir(s) + " pasó su fecha de compromiso (" + fecha + ")." + retraso;
                para(gestor(s), TIPO_VENCIDA, "Solicitud vencida #" + s.getIdServicio(), interno, s);
                for (Usuario admin : usuarioRepository.findByRolIn(List.of(Rol.ADMIN_POLYGRAPH))) {
                    para(admin.getIdUsuario(), TIPO_VENCIDA, "Solicitud vencida #" + s.getIdServicio(), interno, s);
                }
                paraCliente(s, TIPO_VENCIDA, "Tu solicitud #" + s.getIdServicio() + " está vencida",
                        "El estudio de " + evaluado(s) + " pasó su fecha de compromiso (" + fecha + ")." + retraso
                                + " Estamos trabajando para entregarlo cuanto antes.", s);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar el cambio de tiempo de la solicitud {}: {}", s.getIdServicio(), e.getMessage());
        }
    }

    // ───────────────────────── reprogramación ─────────────────────────

    public void alReprogramar(Servicio s, LocalDate anterior, LocalDate nueva, String motivo) {
        try {
            paraCliente(s, TIPO_REPROGRAMADA, "Cambió la fecha de tu solicitud #" + s.getIdServicio(),
                    "La fecha de compromiso del estudio de " + evaluado(s) + " pasó del " + FechasLegibles.larga(anterior) + " al "
                            + FechasLegibles.larga(nueva) + ". Motivo: " + motivo + ".", s);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la reprogramación de la solicitud {}: {}", s.getIdServicio(), e.getMessage());
        }
    }

    // ───────────────────────── pausas ─────────────────────────

    public void alAbrirPausa(Servicio s, SolicitudPausa pausa) {
        if (!avisaPausa(pausa)) return;
        try {
            para(gestor(s), TIPO_PAUSA_ABIERTA, "Se detuvo el reloj de la solicitud #" + s.getIdServicio(),
                    describir(s) + ": " + minuscula(pausa.getMotivo()) + ". Mientras dure, los días hábiles no cuentan para el plazo.", s);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la pausa de la solicitud {}: {}", s.getIdServicio(), e.getMessage());
        }
    }

    /** Se llama después de recalcular, para poder decir la nueva fecha de compromiso. */
    public void alCerrarPausa(Servicio s, SolicitudPausa pausa) {
        if (!avisaPausa(pausa)) return;
        try {
            para(gestor(s), TIPO_PAUSA_CERRADA, "Se reanudó el reloj de la solicitud #" + s.getIdServicio(),
                    describir(s) + ": terminó la pausa (" + minuscula(pausa.getMotivo()) + "), que empezó el "
                            + FechasLegibles.larga(pausa.getInicio() == null ? null : pausa.getInicio().toLocalDate())
                            + ". La fecha de compromiso es el " + FechasLegibles.larga(s.getFechaCompromisoAjustada()) + ".", s);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar el fin de la pausa de la solicitud {}: {}", s.getIdServicio(), e.getMessage());
        }
    }

    private static boolean avisaPausa(SolicitudPausa pausa) {
        return AVISAR_PAUSA_HOJA_DE_VIDA || !SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA.equals(pausa.getMotivo());
    }

    // ───────────────────────── envío ─────────────────────────

    private void para(Long idUsuario, String tipo, String titulo, String mensaje, Servicio s) {
        if (idUsuario == null) return;
        notificaciones.crearParaUsuario(idUsuario, tipo, titulo, mensaje, REFERENCIA, s.getIdServicio() == null ? null : s.getIdServicio().longValue());
    }

    private void paraCliente(Servicio s, String tipo, String titulo, String mensaje, Servicio ref) {
        Cliente c = s.getCliente();
        if (c == null || c.getIdCliente() == null) return;
        for (Usuario u : usuarioRepository.findByIdCliente(c.getIdCliente())) {
            if (Boolean.TRUE.equals(u.getActivo())) para(u.getIdUsuario(), tipo, titulo, mensaje, ref);
        }
    }

    private static Long gestor(Servicio s) {
        return s.getCliente() == null ? null : s.getCliente().getIdGestor();
    }

    private static String evaluado(Servicio s) {
        var cand = s.getCandidato();
        if (cand == null) return "la solicitud #" + s.getIdServicio();
        String nombre = ((cand.getNombres() == null ? "" : cand.getNombres()) + " " + (cand.getApellidos() == null ? "" : cand.getApellidos())).trim();
        return nombre.isEmpty() ? "la solicitud #" + s.getIdServicio() : nombre;
    }

    /** "La solicitud #12 de Ana Pérez (Cliente S.A.S.)". */
    private static String describir(Servicio s) {
        Cliente c = s.getCliente();
        String cliente = c == null ? "" : c.getNombreComercial() != null && !c.getNombreComercial().isBlank() ? c.getNombreComercial()
                : c.getRazonSocial() != null && !c.getRazonSocial().isBlank() ? c.getRazonSocial()
                : ((c.getNombre() == null ? "" : c.getNombre()) + " " + (c.getApellido() == null ? "" : c.getApellido())).trim();
        return "La solicitud #" + s.getIdServicio() + " de " + evaluado(s) + (cliente.isEmpty() ? "" : " (" + cliente + ")");
    }

    private static String minuscula(String texto) {
        return texto == null || texto.isEmpty() ? "" : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String dias(int n) {
        return n + (n == 1 ? " día hábil" : " días hábiles");
    }
}
