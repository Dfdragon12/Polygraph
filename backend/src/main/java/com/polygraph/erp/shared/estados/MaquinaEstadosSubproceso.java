package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Las únicas formas de cambiar el estado de un subproceso (SE-02). Pura: sin Spring ni base de datos, se prueba sola.
 * Cualquier cambio que no esté aquí se rechaza.
 *
 * <pre>
 * Acción                  Desde                              Hacia                       Quién                          Condición
 * ENVIAR_AL_PROGRAMADOR   PENDIENTE                          PROGRAMANDO                 gestor                         solo cita (poligrafía/visita)
 * ASIGNAR                 PENDIENTE                          EN_EJECUCION                gestor                         análisis, con responsable
 * AGENDAR                 PROGRAMANDO                        EN_EJECUCION                programador                    cita, con profesional, día y hora
 * ENVIAR_INFORME          EN_EJECUCION                       FINALIZADO                  el responsable                 —
 * APROBAR                 FINALIZADO                         PUBLICADO                   gestor (o programador en citas) —
 * DEVOLVER                FINALIZADO                         EN_EJECUCION + DEVUELTO     gestor (o programador en citas) motivo obligatorio
 * CITA_NO_REALIZADA       EN_EJECUCION                       REPROGRAMADO                poligrafista, visitador, programador  cita; motivo
 * REPROGRAMAR             REPROGRAMADO                       EN_EJECUCION + REPROGRAMADA programador                    mismo profesional, nueva fecha; motivo
 * REASIGNAR               REPROGRAMADO                       EN_EJECUCION (nuevo)        programador                    otro profesional; motivo
 * CANCELAR                no iniciado                        CANCELADO                   gestor                         motivo obligatorio
 * </pre>
 * El administrador puede todo lo que puede el gestor y el programador.
 */
public final class MaquinaEstadosSubproceso {

    private MaquinaEstadosSubproceso() {}

    public enum Accion {
        ENVIAR_AL_PROGRAMADOR, ASIGNAR, AGENDAR, ENVIAR_INFORME, APROBAR, DEVOLVER,
        CITA_NO_REALIZADA, REPROGRAMAR, REASIGNAR, CANCELAR
    }

    /** Marcas que acompañan al estado (no son estados). */
    public enum Marca { DEVUELTO, REPROGRAMADA, ASIGNADA_NO_REALIZADA, CANCELADA, CANDIDATO_DESISTIO }

    /**
     * Lo que se sabe del subproceso y de quien actúa al pedir la acción.
     *
     * @param esCita            poligrafía o visita (las agenda el programador)
     * @param conResponsable    ya tiene profesional/analista asignado
     * @param conDiaYHora       ya tiene día y hora
     * @param iniciado          el trabajo ya empezó (SE-02)
     * @param conMotivo         se indicó el motivo (y su texto si es "Otro")
     * @param esElResponsable   quien actúa es el responsable asignado
     */
    public record Contexto(boolean esCita, boolean conResponsable, boolean conDiaYHora, boolean iniciado,
                           boolean conMotivo, boolean esElResponsable) {
        public static Contexto vacio() { return new Contexto(false, false, false, false, false, false); }
        public Contexto conEsCita(boolean v) { return new Contexto(v, conResponsable, conDiaYHora, iniciado, conMotivo, esElResponsable); }
        public Contexto conResponsable(boolean v) { return new Contexto(esCita, v, conDiaYHora, iniciado, conMotivo, esElResponsable); }
        public Contexto conDiaYHora(boolean v) { return new Contexto(esCita, conResponsable, v, iniciado, conMotivo, esElResponsable); }
        public Contexto iniciado(boolean v) { return new Contexto(esCita, conResponsable, conDiaYHora, v, conMotivo, esElResponsable); }
        public Contexto conMotivo(boolean v) { return new Contexto(esCita, conResponsable, conDiaYHora, iniciado, v, esElResponsable); }
        public Contexto esElResponsable(boolean v) { return new Contexto(esCita, conResponsable, conDiaYHora, iniciado, conMotivo, v); }
    }

    /** Resultado de una transición válida. */
    public record Transicion(Accion accion, EstadoSubproceso desde, EstadoSubproceso hacia, Set<Marca> marcas) {}

    /** Por qué se rechazó. {@code codigo} distingue permiso (403) de estado/condición (409) y dato faltante (400). */
    public static class TransicionInvalida extends RuntimeException {
        public enum Codigo { SIN_PERMISO, ESTADO_INVALIDO, CONDICION, FALTA_MOTIVO }

        private final Codigo codigo;

        public TransicionInvalida(Codigo codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        public Codigo codigo() { return codigo; }
    }

    private static final Set<Rol> RESPONSABLES = EnumSet.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    /** Valida y devuelve la transición, o lanza {@link TransicionInvalida}. */
    public static Transicion evaluar(Accion accion, EstadoSubproceso actual, Rol actor, Contexto c) {
        if (accion == null || actual == null || actor == null) {
            throw new TransicionInvalida(TransicionInvalida.Codigo.ESTADO_INVALIDO, "Falta indicar la acción, el estado o quién la hace.");
        }
        if (!puede(accion, actor, c)) {
            throw new TransicionInvalida(TransicionInvalida.Codigo.SIN_PERMISO,
                    "Tu perfil no puede " + verbo(accion) + " un subproceso.");
        }
        EstadoSubproceso hacia;
        Set<Marca> marcas = EnumSet.noneOf(Marca.class);
        switch (accion) {
            case ENVIAR_AL_PROGRAMADOR -> {
                exigirDesde(accion, actual, EstadoSubproceso.PENDIENTE);
                exigir(c.esCita(), "Solo la poligrafía, la visita y VerifEye pasan por el programador.");
                hacia = EstadoSubproceso.PROGRAMANDO;
            }
            case ASIGNAR -> {
                exigirDesde(accion, actual, EstadoSubproceso.PENDIENTE);
                exigir(!c.esCita(), "Las citas las agenda el programador: envíala al programador.");
                exigir(c.conResponsable(), "Falta indicar quién lo hará.");
                hacia = EstadoSubproceso.EN_EJECUCION;
            }
            case AGENDAR -> {
                exigirDesde(accion, actual, EstadoSubproceso.PROGRAMANDO);
                exigir(c.esCita(), "Solo las citas se agendan.");
                exigir(c.conResponsable(), "Falta elegir al profesional.");
                exigir(c.conDiaYHora(), "Falta el día y la hora de la cita.");
                hacia = EstadoSubproceso.EN_EJECUCION;
            }
            case ENVIAR_INFORME -> {
                exigirDesde(accion, actual, EstadoSubproceso.EN_EJECUCION);
                hacia = EstadoSubproceso.FINALIZADO;
            }
            case APROBAR -> {
                exigirDesde(accion, actual, EstadoSubproceso.FINALIZADO);
                hacia = EstadoSubproceso.PUBLICADO;
            }
            case DEVOLVER -> {
                exigirDesde(accion, actual, EstadoSubproceso.FINALIZADO);
                exigirMotivo(c);
                hacia = EstadoSubproceso.EN_EJECUCION;
                marcas.add(Marca.DEVUELTO);
            }
            case CITA_NO_REALIZADA -> {
                exigirDesde(accion, actual, EstadoSubproceso.EN_EJECUCION);
                exigir(c.esCita(), "Solo la poligrafía, la visita y VerifEye tienen cita.");
                exigir(!c.iniciado(), "La cita ya se realizó: el informe está iniciado.");
                exigirMotivo(c);
                hacia = EstadoSubproceso.REPROGRAMADO;
            }
            case REPROGRAMAR -> {
                exigirDesde(accion, actual, EstadoSubproceso.REPROGRAMADO);
                exigir(c.conDiaYHora(), "Falta la nueva fecha y hora.");
                exigirMotivo(c);
                hacia = EstadoSubproceso.EN_EJECUCION;
                marcas.add(Marca.REPROGRAMADA);
            }
            case REASIGNAR -> {
                exigirDesde(accion, actual, EstadoSubproceso.REPROGRAMADO);
                exigir(c.conResponsable(), "Falta elegir al otro profesional.");
                exigir(c.conDiaYHora(), "Falta el día y la hora de la cita.");
                exigirMotivo(c);
                hacia = EstadoSubproceso.EN_EJECUCION;
                marcas.add(Marca.ASIGNADA_NO_REALIZADA);
            }
            case CANCELAR -> {
                if (actual == EstadoSubproceso.CANCELADO || actual == EstadoSubproceso.FINALIZADO || actual == EstadoSubproceso.PUBLICADO || c.iniciado()) {
                    throw new TransicionInvalida(TransicionInvalida.Codigo.ESTADO_INVALIDO,
                            "Solo se cancela un subproceso que no ha iniciado (está " + actual.nombre().toLowerCase() + (c.iniciado() ? " e iniciado" : "") + ").");
                }
                exigirMotivo(c);
                hacia = EstadoSubproceso.CANCELADO;
                marcas.add(Marca.CANCELADA);
            }
            default -> throw new TransicionInvalida(TransicionInvalida.Codigo.ESTADO_INVALIDO, "Acción desconocida.");
        }
        return new Transicion(accion, actual, hacia, marcas);
    }

    /** ¿Este perfil puede pedir esta acción? */
    public static boolean puede(Accion accion, Rol actor, Contexto c) {
        if (actor == Rol.ADMIN_POLYGRAPH) return true;
        return switch (accion) {
            case ENVIAR_AL_PROGRAMADOR -> actor == Rol.GESTOR;
            // Hoy el programador también cancela las citas que coordina.
            case CANCELAR -> actor == Rol.GESTOR || (actor == Rol.PROGRAMADOR && c.esCita());
            case ASIGNAR -> actor == Rol.GESTOR;
            case AGENDAR, REPROGRAMAR, REASIGNAR -> actor == Rol.PROGRAMADOR;
            case ENVIAR_INFORME -> RESPONSABLES.contains(actor) && c.esElResponsable();
            // Hoy el programador también revisa y aprueba las citas que coordina; el gestor, todo lo de sus clientes.
            case APROBAR, DEVOLVER -> actor == Rol.GESTOR || (actor == Rol.PROGRAMADOR && c.esCita());
            // El programador y el gestor registran el aviso del evaluado y la cita sin registro; el profesional, la suya.
            case CITA_NO_REALIZADA -> actor == Rol.PROGRAMADOR || (actor == Rol.GESTOR && c.esCita())
                    || ((actor == Rol.POLIGRAFISTA || actor == Rol.VISITADOR) && c.esElResponsable());
        };
    }

    /** Qué acciones puede ofrecer la pantalla ahora mismo (para mostrar solo los botones que valen). */
    public static List<Accion> disponibles(EstadoSubproceso actual, Rol actor, Contexto c) {
        return java.util.Arrays.stream(Accion.values()).filter(a -> {
            try {
                evaluar(a, actual, actor, c.conMotivo(true).conDiaYHora(true).conResponsable(true));
                return true;
            } catch (TransicionInvalida e) {
                return false;
            }
        }).toList();
    }

    /** Qué hacer con cada subproceso cuando el candidato desiste. */
    public enum AlDesistir { CANCELAR, CONTINUAR }

    /**
     * Desistimiento: si NINGÚN subproceso (no cancelado) está iniciado, todos se cancelan; si alguno lo está, los iniciados
     * siguen hasta PUBLICADO, los no iniciados se cancelan y el servicio lleva la marca CANDIDATO_DESISTIO.
     */
    public static AlDesistir alDesistir(EstadoSubproceso estado, boolean iniciado) {
        if (estado == EstadoSubproceso.CANCELADO) return AlDesistir.CONTINUAR;
        return iniciado || estado == EstadoSubproceso.FINALIZADO || estado == EstadoSubproceso.PUBLICADO
                ? AlDesistir.CONTINUAR : AlDesistir.CANCELAR;
    }

    /** El servicio lleva la marca CANDIDATO_DESISTIO cuando al desistir quedó algún subproceso iniciado. */
    public static boolean llevaMarcaDeDesistimiento(List<Boolean> iniciadosNoCancelados) {
        return iniciadosNoCancelados.stream().anyMatch(Boolean::booleanValue);
    }

    private static void exigirDesde(Accion accion, EstadoSubproceso actual, EstadoSubproceso esperado) {
        if (actual != esperado) {
            throw new TransicionInvalida(TransicionInvalida.Codigo.ESTADO_INVALIDO,
                    "No se puede " + verbo(accion) + " desde «" + actual.nombre() + "»; debe estar en «" + esperado.nombre() + "».");
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new TransicionInvalida(TransicionInvalida.Codigo.CONDICION, mensaje);
    }

    private static void exigirMotivo(Contexto c) {
        if (!c.conMotivo()) throw new TransicionInvalida(TransicionInvalida.Codigo.FALTA_MOTIVO, "Falta indicar el motivo.");
    }

    private static String verbo(Accion a) {
        return switch (a) {
            case ENVIAR_AL_PROGRAMADOR -> "enviar al programador";
            case ASIGNAR -> "asignar";
            case AGENDAR -> "agendar";
            case ENVIAR_INFORME -> "enviar el informe de";
            case APROBAR -> "aprobar";
            case DEVOLVER -> "devolver";
            case CITA_NO_REALIZADA -> "registrar la cita no realizada de";
            case REPROGRAMAR -> "reprogramar";
            case REASIGNAR -> "reasignar";
            case CANCELAR -> "cancelar";
        };
    }
}
