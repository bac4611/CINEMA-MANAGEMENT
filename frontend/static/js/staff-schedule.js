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
    let editingItem = null;
    let saving = false;
    let loading = false;
    let dirty = false;
    let loadVersion = 0;
    let loadedDate = '';
    const saveLabel = saveButton.innerHTML;
    const removeButton = document.getElementById('btnRemoveShowtime');
    const endMinutes = item => minutes(item.startTime) + Number(item.duration || minutes(item.endTime) - minutes(item.startTime));
    const timeText = value => String(Math.floor((value % 1440) / 60)).padStart(2,'0') + ':' + String(value % 60).padStart(2,'0');

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
        notice.setAttribute('role', error ? 'alert' : 'status');
    };
    const closeModals = () => document.querySelectorAll('.modal-backdrop').forEach(modal => modal.classList.remove('show'));
    const minutes = value => { const [h, m] = String(value || '00:00').split(':').map(Number); return h * 60 + m; };
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
            block.style.left = `${Math.max(0, start / 1440 * 100)}%`;
            block.style.width = `${Math.min(1440 - start, duration) / 1440 * 100}%`;
            block.textContent = `${item.movieTitle || 'Phim'} · ${item.startTime}`;
            block.title = `${item.movieTitle || 'Phim'} (${item.startTime}–${item.endTime || ''}${start + duration >= 1440 ? ' ngày hôm sau' : ''})`;
            block.addEventListener('click', () => {
                if (loading || saving) return;
                editingItem = item;
                selectedMovie = {movieId:item.movieId,title:item.movieTitle,duration:item.duration,availableFormats:item.availableFormats};
                document.getElementById('scheduleMovieTitle').textContent = item.movieTitle;
                document.getElementById('scheduleStartTime').value = item.startTime;
                scheduleForm.querySelectorAll('[name="scheduleRoom"]').forEach(input => { input.checked = Number(input.value) === Number(item.roomId); });
                removeButton.hidden = false;
                document.getElementById('scheduleFormError').hidden = true;
                scheduleModal.classList.add('show');
            });
            timeline.appendChild(block);
        });
    };
    const load = async () => {
        const version = ++loadVersion;
        const date = dateInput.value;
        loading = true;
        saveButton.disabled = true;
        notice.hidden = true;
        try {
            const response = await fetch(`/Staff/GetSchedule?date=${encodeURIComponent(dateInput.value)}`);
            if (!response.ok) throw new Error();
            const items = await response.json();
            if (version !== loadVersion) return false;
            scheduleItems = items;
            loadedDate = date;
            dirty = false;
            render();
            return true;
        } catch (_) { if (version === loadVersion) { loadedDate = ''; showNotice('Không tải được lịch chiếu từ database. Hãy chọn lại ngày để thử lại.', true); } return false; }
        finally { if(version === loadVersion) { loading = false; saveButton.disabled = !loadedDate; } }
    };

    dateInput.value = today();
    dateInput.addEventListener('change', () => {
        if (dirty && !confirm('Lịch chưa lưu. Bạn muốn bỏ thay đổi để chuyển ngày?')) {dateInput.value=loadedDate;return;}
        load();
    });
    document.querySelectorAll('form[action*="Movie"]').forEach(form => form.addEventListener('submit', event => {
        if(form.dataset.submitting) {event.preventDefault();return;}
        if(!form.checkValidity()) return;
        const formats = form.querySelector('#editFormats');
        if (formats) {
            formats.removeAttribute('name');
            formats.value.split(',').map(value=>value.trim()).filter(Boolean).forEach(value=>{
                const input=document.createElement('input');input.type='hidden';input.name='availableFormats';input.value=value;form.append(input);
            });
        }
        form.dataset.submitting='true';
        form.querySelectorAll('[type="submit"]').forEach(button=>{button.disabled=true;});
    }));
    document.querySelectorAll('.delete-movie-btn').forEach(button => button.addEventListener('click', () => {
        const data=button.closest('.movie-list-item').dataset;
        if(!confirm('Xóa phim "'+data.title+'"? Phim có dữ liệu đặt vé liên quan sẽ không được xóa.')) return;
        const form=document.getElementById('deleteMovieForm');
        form.querySelector('[name="id"]').value=data.movieId;
        form.requestSubmit();
    }));
    document.querySelectorAll('[data-scroll-movies]').forEach(button => button.addEventListener('click',()=>{
        document.querySelector('.movies-list').scrollBy({top:Number(button.dataset.scrollMovies)*300,behavior:'smooth'});
    }));
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
        if (loading || saving || !loadedDate) return;
        editingItem = null;
        scheduleForm.reset();
        removeButton.hidden = true;
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
        if (loading || saving || dateInput.value !== loadedDate) return;
        const start = minutes(startTime), duration = Number(selectedMovie.duration), end = start + duration;
        if (!Number.isFinite(start) || start < 0 || start >= 1440 || duration <= 0) {error.textContent='Giờ bắt đầu phải từ 00:00 đến 23:59.';error.hidden=false;return;}
        if(editingItem && rooms.length !== 1) {error.textContent='Khi sửa suất chiếu, chỉ chọn một phòng.';error.hidden=false;return;}
        const conflict=rooms.some(room => scheduleItems.some(item => item !== editingItem && Number(item.roomId) === Number(room.value) && start < endMinutes(item) && end > minutes(item.startTime)));
        if(conflict) {error.textContent='Phòng đã có suất chiếu trùng giờ. Hãy sửa/xóa suất cũ hoặc chọn giờ khác.';error.hidden=false;return;}
        const added=rooms.map(room => ({showtimeId:editingItem?.showtimeId || 0,movieId:Number(selectedMovie.movieId),movieTitle:selectedMovie.title,roomId:Number(room.value),startTime,endTime:timeText(end),duration,availableFormats:selectedMovie.availableFormats}));
        scheduleItems = scheduleItems.filter(item => item !== editingItem).concat(added);
        dirty = true;
        render(); closeModals();
        showNotice('Lịch đã thay đổi trên màn hình. Bấm Lưu lịch để ghi vào database.');
    });
    removeButton?.addEventListener('click', () => {
        if (!editingItem || saving || loading) return;
        if (!confirm('Bỏ suất chiếu này khỏi lịch? Thay đổi được áp dụng khi bấm Lưu lịch.')) return;
        scheduleItems = scheduleItems.filter(item => item !== editingItem);
        dirty = true;
        render(); closeModals();
        showNotice('Suất chiếu đã được bỏ khỏi bản nháp. Bấm Lưu lịch để xác nhận.');
    });
    saveButton?.addEventListener('click', async () => {
        if (saving || loading || loadedDate !== dateInput.value) return;
        saving = true;
        saveButton.disabled = true;
        dateInput.disabled = true;
        saveButton.textContent = 'ĐANG LƯU...';
        saveButton.setAttribute('aria-busy', 'true');
        showNotice('Đang lưu lịch chiếu vào database...');
        const headers = { 'Content-Type': 'application/json' };
        if (csrf() && csrfHeader()) headers[csrfHeader()] = csrf();
        const items = scheduleItems.map(item => ({ showtimeId: Number(item.showtimeId || 0), movieId: Number(item.movieId), roomId: Number(item.roomId), startTime: item.startTime, endTime: item.endTime || '' }));
        try {
            const response = await fetch('/Staff/SaveSchedule', { method: 'POST', headers, body: JSON.stringify({ date: dateInput.value, items }) });
            const result = await response.json().catch(() => ({}));
            if (response.redirected || response.status === 401 || response.status === 403)
                throw new Error('Phiên đăng nhập đã hết hạn hoặc không có quyền. Vui lòng đăng nhập lại.');
            if (!response.ok) throw new Error(result.message || 'Không lưu được lịch chiếu (HTTP ' + response.status + ').');
            const refreshed = await load();
            if (refreshed) showNotice('Đã lưu lịch ' + loadedDate + '. Thêm: ' + (result.created || 0) + ', cập nhật: ' + (result.updated || 0) + ', xóa: ' + (result.deleted || 0) + '.');
            else showNotice('Lịch đã lưu vào database nhưng chưa tải lại được. Vui lòng tải lại trang.', true);
        } catch (error) { showNotice(error.message || 'Không lưu được lịch chiếu.', true); }
        finally {saving=false;saveButton.disabled=loading || !loadedDate;dateInput.disabled=false;saveButton.innerHTML=saveLabel;saveButton.removeAttribute('aria-busy');}
    });
    load();
})();
