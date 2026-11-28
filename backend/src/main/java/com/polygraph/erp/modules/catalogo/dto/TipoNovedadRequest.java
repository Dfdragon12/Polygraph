package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** roles: a qué rol ejecutor le aparece el tipo — ANALISTA_INTERNO, POLIGRAFISTA y/o VISITADOR. */
public record TipoNovedadRequest(
        @NotNull(message = "Elige la categoría.") Integer idCategoriaNovedad,
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.") String nombre,
        @NotEmpty(message = "Elige al menos un rol al que le aparezca.") Set<String> roles,
        @Size(max = 300, message = "El texto de ayuda no puede superar 300 caracteres.") String textoAyuda,
        Integer orden,
        // null = no cambiar (al crear: no visible)
        Boolean visibleCliente,
        // null = no cambiar (al crear: no pausa el reloj)
        Boolean pausaReloj,
        // POLYGRAPH | CANDIDATO | CLIENTE; null = no cambiar (al crear: POLYGRAPH)
        String imputableA) {

    public TipoNovedadRequest(Integer idCategoriaNovedad, String nombre, Set<String> roles, String textoAyuda, Integer orden) {
        this(idCategoriaNovedad, nombre, roles, textoAyuda, orden, null, null, null);
    }
}
