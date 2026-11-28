// PC-02 · La conclusión que escribe el ejecutor no lleva «Favorable» ni «Desfavorable»: el resultado lo da el nivel Bajo, Medio o Alto.
// El servidor (ConclusionFormato.java) es quien manda y vuelve a validar al enviar y al publicar; esto solo avisa antes, mientras se escribe.
// Misma regla: la palabra completa, sin importar mayúsculas; «favorablemente» es otra palabra y no cuenta. (Sin lookbehind: Safari viejo no lo entiende.)
export const AYUDA_CONCLUSION = 'No escribas Favorable o Desfavorable: el resultado lo da el nivel Bajo, Medio o Alto.'
export const AVISO_FAVORABLE = 'La conclusión no debe decir «Favorable» ni «Desfavorable»: el resultado lo da el nivel Bajo, Medio o Alto. Quita esa palabra para continuar.'

const FAVORABLE = /(^|[^\p{L}])(des)?favorables?(?![\p{L}])/iu

export const contieneFavorable = (texto) => !!texto && FAVORABLE.test(texto)
