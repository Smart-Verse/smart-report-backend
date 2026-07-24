function onCurrency(value) {
    return Number(value).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'})
}

function onDateTime(value) {
    return new Date(value).toLocaleString('pt-BR', {dateStyle: 'short', timeStyle: 'short'})
}

function itemTotal(item) {
    return (Number(item.quantity) * Number(item.unitPrice)) - Number(item.discount || 0)
}
