package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.shared.dto.TiempoServicio;

import java.time.LocalDate;
import java.util.List;

/**
 * Un servicio en la lista filtrada por el semáforo (SE-04). {@code resumen}: cuántos subprocesos tiene en cada estado (solo los que
 * tienen alguno), para «Pendiente 1 · Programando 2 · En ejecución 1 · Publicado 1». {@code publicados}/{@code vigentes}: el avance (los
 * cancelados no cuentan). {@code etiquetaPublicacion}: «Publicado parcial», «Estudio completo publicado» o null. {@code subprocesos}: los del
 * servicio con {@code resaltado = true} los que están en el estado filtrado.
 */
public record ServicioSemaforoFila(
        Integer idServicio,
        String evaluado,
        String cedula,
        String cargo,
        String proceso,
        String cliente,
        LocalDate fechaSolicitud,
        List<ResumenEstado> resumen,
        int publicados,
        int vigentes,
        String etiquetaPublicacion,
        List<SubprocesoEnFila> subprocesos,
        TiempoServicio tiempo) {

    public record ResumenEstado(String codigo, String nombre, String color, int cantidad) {
        /** «Pendiente 1 · Programando 2...»: cuántos subprocesos hay en cada estado (solo los que tienen alguno), en el orden de pantalla. */
        public static java.util.List<ResumenEstado> de(java.util.Collection<com.polygraph.erp.shared.enums.EstadoSubproceso> estados) {
            java.util.List<ResumenEstado> r = new java.util.ArrayList<>();
            for (com.polygraph.erp.shared.enums.EstadoSubproceso e : com.polygraph.erp.shared.enums.EstadoSubproceso.enOrden()) {
                int n = (int) estados.stream().filter(x -> x == e).count();
                if (n > 0) r.add(new ResumenEstado(e.name(), e.nombre(), e.color(), n));
            }
            return r;
        }
    }

    public record SubprocesoEnFila(Long id, String nombre, String estado, String estadoNombre, boolean resaltado) {}
}
