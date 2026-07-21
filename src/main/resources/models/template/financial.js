function onCurrency(value) {
    return Number(value).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'})
}

function onDateFormat(value) {
    return new Date(value).toLocaleDateString('pt-BR')
}

function margin(income, expense) {
    return (((income - expense) / income) * 100).toFixed(1)
}
