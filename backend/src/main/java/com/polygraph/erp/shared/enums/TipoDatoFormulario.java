package com.polygraph.erp.shared.enums;

public enum TipoDatoFormulario {
    TEXTO, NUMERO, FECHA, BOOLEANO, ARCHIVO, LISTA,
    // Mismo catálogo de ciudades/barrios que usa el candidato — el valor se guarda como texto
    // (el nombre), igual que TEXTO/LISTA; el frontend decide cómo renderizarlo (ver
    // InputPorTipo en Formulario.jsx). No requieren `opciones` como LISTA.
    CIUDAD, BARRIO
}
