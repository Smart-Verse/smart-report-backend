function onDateFormat(value) {
    return new Date(value).toLocaleDateString('pt-BR')
}

function onCurrency(value) {
    return Number(value).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'})
}

function activeCount(items) {
    return items.filter(item => item.status === 'Ativo').length
}
