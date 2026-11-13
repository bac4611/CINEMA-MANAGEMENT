(() => {
    const dateInput = document.getElementById('scheduleDate');
    const saveButton = document.getElementById('btnSaveSchedule');
    const notice = document.getElementById('scheduleNotice');
    const search = document.getElementById('movieSearchInput');
    const createModal = document.getElementById('movieCreateModal');
    const editModal = document.getElementById('movieEditorModal');
    const scheduleModal = document.getElementById('movieScheduleModal');
    const scheduleForm = document.getElementById('movieScheduleForm');
    const timelines = [...document.querySelectorAll('.mr-timeline')];
    let selectedMovie = null;
    let scheduleItems = [];

    const today = () => {
        const now = new Date();
        return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    };
    const csrf = () => document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = () => document.querySelector('meta[name="_csrf_header"]')?.content;
    const showNotice = (message, error = false) => {
        notice.textContent = message;
        notice.className = `staff-alert ${error ? 'staff-alert-error' : 'staff-alert-success'}`;
        notice.hidden = false;
    };
    const closeModals = () => document.querySelectorAll('.modal-backdrop').forEach(modal => modal.classList.remove('show'));
    const minutes = value => { const [h, m] = String(value || '08:00').split(':').map(Number); return h * 60 + m; };
    const render = () => {
        timelines.forEach(timeline => timeline.replaceChildren());
        scheduleItems.forEach(item => {
            const timeline = timelines.find(node => Number(node.dataset.roomId) === Number(item.roomId));
            if (!timeline) return;
            const block = document.createElement('button');
            block.type = 'button';
            block.className = 'schedule-block';
            const start = minutes(item.startTime);
            const duration = Number(item.duration || (minutes(item.endTime) - start) || 120);
            block.style.left = `${Math.max(0, (start - 480) / 960 * 100)}%`;
            block.style.width = `${Math.max(5, duration / 960 * 100)}%`;
            block.textContent = `${item.movieTitle || 'Phim'} · ${item.startTime}`;
            block.title = `${item.movieTitle || 'Phim'} (${item.startTime}–${item.endTime || ''})`;
            timeline.appendChild(block);
        });
    };
    const load = async () => {
        notice.hidden = true;
        try {
            const response = await fetch(`/Staff/GetSchedule?date=${encodeURIComponent(dateInput.value)}`);
            if (!response.ok) throw new Error();
            scheduleItems = await response.json();
            render();
        } catch (_) { showNotice('Không tải được lịch chiếu từ database.', true); }
    };

    dateInput.value = today();
    dateInput.addEventListener('change', load);
    document.getElementById('btnAddMovie')?.addEventListener('click', () => createModal.classList.add('show'));
    document.querySelectorAll('.btn-close-modal').forEach(button => button.addEventListener('click', closeModals));
    document.querySelectorAll('.modal-backdrop').forEach(modal => modal.addEventListener('click', event => { if (event.target === modal) closeModals(); }));
    search?.addEventListener('input', () => {
        let found = false;
        document.querySelectorAll('.movie-list-item').forEach(item => {
            const visible = (item.dataset.title || '').toLowerCase().includes(search.value.trim().toLowerCase());
            item.hidden = !visible; found ||= visible;
        });
        document.getElementById('movieSearchEmpty').hidden = found;
    });
    document.querySelectorAll('.schedule-movie-btn').forEach(button => button.addEventListener('click', () => {
        selectedMovie = button.closest('.movie-list-item').dataset;
        document.getElementById('scheduleMovieTitle').textContent = selectedMovie.title;
        document.getElementById('scheduleMovieDuration').textContent = `${selectedMovie.duration} min`;
        document.getElementById('scheduleFormError').hidden = true;
        scheduleModal.classList.add('show');
    }));
    document.querySelectorAll('.edit-movie-btn').forEach(button => button.addEventListener('click', () => {
        const data = button.closest('.movie-list-item').dataset;
        const fields = { editMovieId: 'movieId', editTitle: 'title', editSynopsis: 'synopsis', editReleaseDate: 'releaseDate', editDuration: 'duration', editRating: 'rating', editGenre: 'genre', editDirector: 'director', editCast: 'cast', editLanguage: 'language', editCountry: 'country', editPoster: 'poster', editTrailer: 'trailer', editFormats: 'availableFormats' };
        Object.entries(fields).forEach(([id, key]) => { const field = document.getElementById(id); if (field) field.value = data[key] || ''; });
        editModal.classList.add('show');
    }));
    scheduleForm?.addEventListener('submit', event => {
        event.preventDefault();
        const rooms = [...scheduleForm.querySelectorAll('input[name="scheduleRoom"]:checked')];
        const startTime = document.getElementById('scheduleStartTime').value;
        const error = document.getElementById('scheduleFormError');
        if (!selectedMovie || !startTime || rooms.length === 0) { error.textContent = 'Vui lòng chọn giờ bắt đầu và ít nhất một phòng.'; error.hidden = false; return; }
        rooms.forEach(room => scheduleItems.push({ showtimeId: 0, movieId: Number(selectedMovie.movieId), movieTitle: selectedMovie.title, roomId: Number(room.value), startTime, duration: Number(selectedMovie.duration), availableFormats: selectedMovie.availableFormats }));
        render(); closeModals();
    });
    saveButton?.addEventListener('click', async () => {
        const headers = { 'Content-Type': 'application/json' };
        if (csrf() && csrfHeader()) headers[csrfHeader()] = csrf();
        const items = scheduleItems.map(item => ({ showtimeId: Number(item.showtimeId || 0), movieId: Number(item.movieId), roomId: Number(item.roomId), startTime: item.startTime, endTime: item.endTime || '' }));
        try {
            const response = await fetch('/Staff/SaveSchedule', { method: 'POST', headers, body: JSON.stringify({ date: dateInput.value, items }) });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message || 'Không lưu được lịch chiếu.');
            showNotice(result.message || 'Lưu lịch chiếu thành công.'); await load();
        } catch (error) { showNotice(error.message || 'Không lưu được lịch chiếu.', true); }
    });
    load();
})();
