package com.polygraph.erp.shared.enums;

/** Nota crédito (reduce lo cobrado, restituye saldo/cupo real) o nota débito (registra un mayor
 *  valor, no mueve saldo/cupo) — ver NotaAjusteService. */
public enum TipoNotaAjuste {
    CREDITO,
    DEBITO
}
