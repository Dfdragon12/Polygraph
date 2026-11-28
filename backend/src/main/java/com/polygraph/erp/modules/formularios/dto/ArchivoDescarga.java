package com.polygraph.erp.modules.formularios.dto;

import org.springframework.core.io.Resource;

public record ArchivoDescarga(Resource recurso, String nombreArchivo, String tipoContenido) {}
