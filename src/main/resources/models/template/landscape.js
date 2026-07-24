function onCurrency(value) {
    return Number(value).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'})
}

function onDateFormat(value) {
    return new Date(value).toLocaleDateString('pt-BR')
}

function progress(value, target) {
    if (!Number(target)) return 0
    return Math.min(100, Math.round((Number(value) / Number(target)) * 100))
}
