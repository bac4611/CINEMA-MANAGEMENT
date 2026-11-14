(() => {
    const form = document.getElementById('seatForm');
    if (!form) return;
    const seats = [...form.querySelectorAll('.seat')];
    const confirm = document.getElementById('confirmSeatButton');
    const list = document.getElementById('selectedSeatsList');
    const error = document.getElementById('seatError');
    const money = value => new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value);
    let pending = false;
    function render() {
        const selected = seats.filter(seat => seat.classList.contains('selected'));
        list.replaceChildren();
        if (!selected.length) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.textContent = 'Chọn ghế từ sơ đồ';
            list.append(empty);
        }
        selected.forEach(seat => {
            const line = document.createElement('div'), label = document.createElement('span'), price = document.createElement('span');
            line.className = 'selected-line';
            label.textContent = (seat.dataset.seatType === 'Regular' ? 'Standard' : seat.dataset.seatType) + ' ' + seat.dataset.seatCode;
            price.textContent = money(Number(seat.dataset.price));
            line.append(label, price);
            list.append(line);
        });
        document.getElementById('ticketCount').textContent = 'Số lượng ghế (' + selected.length + ')';
        document.getElementById('ticketSubtotal').textContent = money(selected.reduce((sum, seat) => sum + Number(seat.dataset.price), 0));
        confirm.disabled = pending || selected.length === 0 || selected.length > 5;
    }
    seats.filter(seat => !seat.disabled).forEach(seat => seat.addEventListener('click', async () => {
        if (pending) return;
        pending = true;
        error.hidden = true;
        seat.disabled = true;
        seat.setAttribute('aria-busy', 'true');
        render();
        const body = new URLSearchParams({seatId:seat.dataset.seatId, select:String(!seat.classList.contains('selected'))});
        const csrf = form.querySelector('input[name="_csrf"]');
        if (csrf) body.append(csrf.name, csrf.value);
        try {
            const response = await fetch(form.dataset.holdUrl, {method:'POST', body, headers:{Accept:'application/json'}});
            const result = await response.json().catch(() => ({}));
            if (response.status === 409 && /ghế/i.test(result.message || '')) {
                seat.classList.remove('selected');
                seat.classList.add('occupied');
            }
            if (!response.ok) throw new Error(result.message || 'Không thể cập nhật ghế.');
            seat.classList.toggle('selected', result.selected);
        } catch (failure) {
            error.textContent = failure.message || 'Không thể kết nối tới máy chủ để giữ ghế.';
            error.hidden = false;
        } finally {
            pending = false;
            seat.disabled = seat.classList.contains('occupied');
            seat.removeAttribute('aria-busy');
            seat.setAttribute('aria-pressed', String(seat.classList.contains('selected')));
            render();
        }
    }));
    form.addEventListener('submit', event => { if (pending || confirm.disabled) event.preventDefault(); });
    render();
})();
