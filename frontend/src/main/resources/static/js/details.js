(() => {
    const tabs = [...document.querySelectorAll('.showtimes-date-tab')];
    const panels = [...document.querySelectorAll('.showtimes-times[data-date]')];
    panels.forEach(panel => {
        const empty = !panel.querySelector('.showtimes-time-card');
        panel.querySelector('.showtimes-times__grid').hidden = empty;
        panel.querySelector('.showtimes-empty').hidden = !empty;
    });
    function selectTab(tab) {
        tabs.forEach(item => {
            item.classList.toggle('active', item === tab);
            item.setAttribute('aria-selected', String(item === tab));
            item.tabIndex = item === tab ? 0 : -1;
        });
        panels.forEach(panel => { panel.hidden = panel.dataset.date !== tab.dataset.date; });
    }
    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => selectTab(tab));
        tab.addEventListener('keydown', event => {
            const next = event.key === 'ArrowRight' ? (index + 1) % tabs.length
                : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length
                : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : null;
            if (next === null) return;
            event.preventDefault();
            selectTab(tabs[next]);
            tabs[next].focus();
        });
    });
    if (tabs.length) selectTab(tabs[0]);

    const modal = document.getElementById('trailerModal');
    const frame = document.getElementById('trailerIframe');
    const openButton = document.getElementById('btn-watch-trailer');
    const closeButton = document.getElementById('closeTrailer');
    let previousOverflow = '';
    function embed(raw) {
        try {
            const url = new URL(raw);
            const host = url.hostname.replace(/^www\./, '');
            let id = '';
            if (host === 'youtu.be') id = url.pathname.split('/')[1];
            if (host === 'youtube.com' || host === 'm.youtube.com' || host === 'youtube-nocookie.com') {
                id = url.searchParams.get('v') || url.pathname.match(/^\/(?:embed|shorts)\/([\w-]+)/)?.[1] || '';
            }
            return /^[\w-]+$/.test(id) ? 'https://www.youtube.com/embed/' + id + '?autoplay=1' : '';
        } catch { return ''; }
    }
    function close() {
        frame.removeAttribute('src');
        modal.classList.remove('active');
        modal.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = previousOverflow;
        openButton?.focus();
    }
    openButton?.addEventListener('click', () => {
        const url = embed(openButton.dataset.trailer);
        if (!url) return;
        frame.src = url;
        previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        modal.classList.add('active');
        modal.setAttribute('aria-hidden', 'false');
        closeButton.focus();
    });
    closeButton?.addEventListener('click', close);
    modal?.addEventListener('click', event => { if (event.target === modal) close(); });
    modal?.addEventListener('keydown', event => {
        if (event.key === 'Escape') close();
        if (event.key === 'Tab') {
            if (event.shiftKey && document.activeElement === closeButton) { event.preventDefault(); frame.focus(); }
            else if (!event.shiftKey && document.activeElement === frame) { event.preventDefault(); closeButton.focus(); }
        }
    });

    const form = document.querySelector('[data-review-form]');
    if (!form) return;
    const input = form.querySelector('[data-review-rating]');
    const range = form.querySelector('[data-review-rating-range]');
    const label = form.querySelector('[data-review-rating-label]');
    const stars = [...form.querySelectorAll('[data-review-star]')];
    const error = form.querySelector('[data-review-error]');
    const comment = form.querySelector('[data-review-comment]');
    function setRating(value) {
        const rating = Math.max(0, Math.min(5, Number(value) || 0));
        input.value = rating ? rating.toFixed(2) : '';
        range.value = rating;
        label.textContent = rating.toFixed(2) + ' / 5';
        stars.forEach(star => {
            const score = Number(star.dataset.ratingValue);
            star.classList.toggle('is-active', rating >= score);
            star.classList.toggle('is-soft-active', rating > score - 1 && rating < score);
            star.setAttribute('aria-pressed', String(rating >= score));
        });
        error.textContent = '';
    }
    stars.forEach(star => star.addEventListener('click', () => setRating(star.dataset.ratingValue)));
    range.addEventListener('input', () => setRating(range.value));
    comment.addEventListener('input', () => { error.textContent = ''; });
    form.addEventListener('submit', event => {
        if (!input.value || !comment.value.trim()) {
            event.preventDefault();
            error.textContent = !input.value ? 'Vui lòng chọn điểm đánh giá.' : 'Vui lòng nhập nội dung đánh giá.';
            if (input.value) comment.focus();
            return;
        }
        const submit = form.querySelector('[data-review-submit]');
        submit.disabled = true;
        submit.textContent = 'Đang gửi...';
    });
    setRating(input.value);
})();
