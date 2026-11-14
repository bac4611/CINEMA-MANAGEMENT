(() => {
    const form = document.getElementById('concessionForm');
    if (!form) return;
    const cards = [...document.querySelectorAll('.concession-item')];
    const error = document.getElementById('comboError');
    const summary = document.getElementById('selectedCombos');
    const submit = form.querySelector('button');
    const ticketTotal = Number(document.querySelector('[data-ticket-total]').dataset.ticketTotal);
    const money = n => new Intl.NumberFormat('vi-VN', {style:'currency',currency:'VND'}).format(n);
    let busy = false;
    function render() {
        let total = 0;
        summary.replaceChildren();
        cards.forEach(card => {
            const quantity = Number(card.querySelector('[data-quantity]').textContent);
            const stock = Number(card.dataset.stock);
            const label = card.querySelector('.stock-label');
            label.textContent = stock ? 'Còn ' + stock + ' phần' : 'Tạm hết hàng';
            label.classList.toggle('is-empty', stock === 0);
            card.classList.toggle('is-selected', quantity > 0);
            card.querySelector('[data-change="-1"]').disabled = busy || quantity <= 0;
            card.querySelector('[data-change="1"]').disabled = busy || quantity >= Math.min(stock, 10);
            if (!quantity) return;
            const amount = quantity * Number(card.dataset.price);
            total += amount;
            const row = document.createElement('div'), name = document.createElement('span'), price = document.createElement('strong');
            row.className = 'addons-selected-line';
            name.textContent = card.dataset.name + ' × ' + quantity;
            price.textContent = money(amount);
            row.append(name, price);
            summary.append(row);
        });
        if (!summary.children.length) {
            const empty = document.createElement('p');
            empty.textContent = 'Chưa chọn món. Bạn có thể tiếp tục với vé đã chọn.';
            summary.append(empty);
        }
        document.getElementById('concessionSubtotal').textContent = money(total);
        document.getElementById('grandTotal').textContent = money(ticketTotal + total);
        submit.disabled = busy;
    }
    cards.forEach(card => card.querySelectorAll('[data-change]').forEach(button => button.addEventListener('click', async () => {
        if (busy) return;
        busy = true;
        error.hidden = true;
        render();
        const body = new URLSearchParams({comboId:card.dataset.comboId,change:button.dataset.change});
        const csrf = form.querySelector('input[name="_csrf"]');
        if (csrf) body.append(csrf.name, csrf.value);
        try {
            const response = await fetch(form.dataset.updateUrl,{method:'POST',body,headers:{Accept:'application/json'}});
            const result = await response.json().catch(() => ({}));
            if (Number.isInteger(result.stockQuantity)) card.dataset.stock = result.stockQuantity;
            if (!response.ok) throw new Error(result.message || 'Không thể cập nhật combo.');
            card.querySelector('[data-quantity]').textContent = result.quantity;
        } catch (failure) {
            error.textContent = failure.message || 'Không thể kết nối tới máy chủ.';
            error.hidden = false;
        } finally { busy = false; render(); }
    })));
    form.addEventListener('submit',event => {if(busy) event.preventDefault();});
    render();
})();
