package com.polygraph.erp.modules.gestor.dto;

public record EmpleadoAsignableResponse(
        Long idUsuario,
        String nombre,
        String apellido,
        String rol,
        // true si este empleado tiene la ciudad del servicio entre sus ciudades asignadas
        // (ver módulo Equipo Polygraph) — se usa para priorizar/resaltar en el selector.
        boolean cubreLaCiudad,
        // Solo aplica a POLIGRAFISTA y cuando cubreLaCiudad es true: la sala que le corresponde en esa ciudad.
        Integer numeroSala
) {}
