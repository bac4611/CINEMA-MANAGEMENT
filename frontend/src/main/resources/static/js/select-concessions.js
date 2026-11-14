(() => {
    const form = document.getElementById('concessionForm');
    if (!form) return;
    const cards = [...form.querySelectorAll('.product-card')];
    const list = document.getElementById('selectedConcessionsList');
    const error = document.getElementById('comboError');
    const skip = document.getElementById('skipAddonsButton');
    const submit = form.querySelector('[type="submit"]');
    const ticketTotal = Number(document.getElementById('contentSummary').dataset.ticketTotal);
    const money = value => new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value);
    let busy = false;
    function render() {
        let total = 0;
        list.replaceChildren();
        cards.forEach(card => {
            const quantity = Number(card.querySelector('.quantity-input').value);
            const stock = Number(card.dataset.stock);
            card.querySelector('.quantity-value').textContent = quantity;
            card.querySelector('.decrease').disabled = busy || quantity <= 0;
            card.querySelector('.plus').disabled = busy || quantity >= Math.min(10, stock);
            const stockLabel = card.querySelector('.stock');
            stockLabel.textContent = 'Tồn kho: ' + stock;
            stockLabel.classList.toggle('out-of-stock', stock <= 0);
            if (quantity <= 0) return;
            const amount = Number(card.dataset.price) * quantity;
            total += amount;
            const line = document.createElement('div');
            line.className = 'compact-line';
            const label = document.createElement('span'), price = document.createElement('span');
            label.textContent = card.dataset.name + ' x ' + quantity;
            price.textContent = money(amount);
            line.append(label, price);
            list.append(line);
        });
        if (!list.children.length) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.textContent = 'Không có combo nào được chọn';
            list.append(empty);
        }
        document.getElementById('concessionSubtotal').textContent = money(total);
        document.getElementById('grandTotal').textContent = money(ticketTotal + total);
        submit.disabled = busy;
        skip.disabled = busy;
    }
    async function post(url, values = {}) {
        const body = new URLSearchParams(values);
        const csrf = form.querySelector('input[name="_csrf"]');
        if (csrf) body.append(csrf.name, csrf.value);
        const response = await fetch(url, {method:'POST', body, headers:{Accept:'application/json'}});
        const result = await response.json().catch(() => ({}));
        if (!response.ok) {
            const failure = new Error(result.message || 'Không thể cập nhật combo.');
            failure.result = result;
            throw failure;
        }
        return result;
    }
    function showError(failure) {
        error.textContent = failure.message || 'Không thể kết nối tới máy chủ để giữ combo.';
        error.hidden = false;
    }
    cards.forEach(card => card.querySelectorAll('[data-change]').forEach(button => {
        button.addEventListener('click', async () => {
            if (busy) return;
            busy = true;
            error.hidden = true;
            card.setAttribute('aria-busy', 'true');
            render();
            try {
                const result = await post(form.dataset.updateUrl, {comboId:card.dataset.comboId, change:button.dataset.change});
                card.querySelector('.quantity-input').value = result.quantity;
                card.dataset.stock = result.stockQuantity;
            } catch (failure) {
                if (Number.isInteger(failure.result?.stockQuantity)) card.dataset.stock = failure.result.stockQuantity;
                showError(failure);
            } finally {
                busy = false;
                card.removeAttribute('aria-busy');
                render();
            }
        });
    }));
    skip.addEventListener('click', async () => {
        if (busy) return;
        busy = true;
        error.hidden = true;
        render();
        try {
            await post(form.dataset.clearUrl);
            cards.forEach(card => { card.querySelector('.quantity-input').value = 0; });
            // Continue only after the server has released the reserved combos.
            busy = false;
            render();
            form.requestSubmit();
        } catch (failure) {
            busy = false;
            render();
            showError(failure);
        }
    });
    form.addEventListener('submit', event => {
        if (busy) { event.preventDefault(); return; }
        busy = true;
        render();
    });
    render();
})();
