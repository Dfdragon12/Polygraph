package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.PastaEstado;

/**
 * PC-01: la ÚNICA definición de «el estudio está listo para el cliente»: estudio PUBLICADO, pasta EMITIDA y su PDF vigente ya armado (LISTO y guardado).
 * La usan la descarga, el detalle, el Dashboard y el aviso al cliente: ninguna pantalla decide «listo» por su cuenta.
 */
public final class EstudioListo {

    private EstudioListo() {}

    public static boolean paraCliente(Servicio s, DocumentoEstudio vigente) {
        return estudioPublicado(s) && vigente != null && Boolean.TRUE.equals(vigente.getVigente())
                && DocumentoEstudio.LISTO.equals(vigente.getEstado()) && vigente.getRuta() != null;
    }

    public static boolean estudioPublicado(Servicio s) {
        return s != null && s.getEstado() == EstadoServicio.PUBLICADO && s.getPastaEstado() == PastaEstado.EMITIDA;
    }
}
