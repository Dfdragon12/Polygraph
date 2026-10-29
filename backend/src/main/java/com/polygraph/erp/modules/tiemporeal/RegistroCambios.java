package com.polygraph.erp.modules.tiemporeal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

/** Junta, durante una transacción, qué cambió (servicios, subprocesos, novedades, hojas de vida,
 *  candidatos) y lo entrega UNA vez, después del commit, a ResolutorCambios — que averigua a quién
 *  le incumbe y avisa por CanalCambios. Si la transacción se revierte, no se avisa nada.
 *
 *  Estático porque lo llama CambiosEntityListener, que instancia JPA (no Spring). */
@Slf4j
public final class RegistroCambios {

    private RegistroCambios() {}

    private static volatile ResolutorCambios resolutor;

    static void usar(ResolutorCambios r) { resolutor = r; }

    /** Lo que cambió en esta transacción (ids). */
    public static final class Pendientes {
        final Set<Integer> servicios = new HashSet<>();
        final Set<Long> subprocesos = new HashSet<>();
        final Set<Long> novedades = new HashSet<>();
        final Set<Long> hojasVida = new HashSet<>();
        final Set<Long> candidatos = new HashSet<>();
        /** Cosas que no son de un servicio pero el tablero del administrador muestra en vivo: documentos
         *  de clientes, pagos, reversiones, clientes, usuarios, ciudades, trabajos, riesgo. */
        final Set<String> temasAdmin = new HashSet<>();

        boolean vacio() {
            return servicios.isEmpty() && subprocesos.isEmpty() && novedades.isEmpty() && hojasVida.isEmpty()
                    && candidatos.isEmpty() && temasAdmin.isEmpty();
        }
    }

    static void servicio(Integer id) { if (id != null) pendientes().servicios.add(id); }
    static void subproceso(Long id) { if (id != null) pendientes().subprocesos.add(id); }
    static void novedad(Long id) { if (id != null) pendientes().novedades.add(id); }
    static void hojaVida(Long id) { if (id != null) pendientes().hojasVida.add(id); }
    static void candidato(Long id) { if (id != null) pendientes().candidatos.add(id); }
    static void admin(String tema) { if (tema != null) pendientes().temasAdmin.add(tema); }

    private static Pendientes pendientes() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Sin transacción (raro): se avisa de una vez con lo que haya.
            Pendientes p = new Pendientes();
            despachar(p, true);
            return p;
        }
        Pendientes p = (Pendientes) TransactionSynchronizationManager.getResource(Pendientes.class);
        if (p == null) {
            Pendientes nuevo = new Pendientes();
            TransactionSynchronizationManager.bindResource(Pendientes.class, nuevo);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(Pendientes.class);
                    if (estado == STATUS_COMMITTED) despachar(nuevo, false);
                }
            });
            p = nuevo;
        }
        return p;
    }

    /** diferido=true: se llama antes de anotar (sin transacción) — se despacha en el siguiente ciclo. */
    private static void despachar(Pendientes p, boolean diferido) {
        ResolutorCambios r = resolutor;
        if (r == null) return;
        if (diferido) {
            r.resolverDespues(p);
        } else if (!p.vacio()) {
            r.resolverYAvisar(p);
        }
    }
}
