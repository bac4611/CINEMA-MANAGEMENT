// HOLD CHANGE: đồng hồ chỉ hiển thị thời gian server đã cấp, không tự tạo thêm 10 phút ở trình duyệt.
(() => {
    const timers = document.querySelectorAll("[data-booking-countdown]");

    timers.forEach(timer => {
        const output = timer.querySelector("[data-countdown-value]");
        const expiresAt = Date.parse(timer.dataset.expiresAt || "");
        const serverNow = Date.parse(timer.dataset.serverNow || "");
        const expiredUrl = timer.dataset.expiredUrl;
        let redirected = false;

        if (!output || Number.isNaN(expiresAt) || Number.isNaN(serverNow) || !expiredUrl) {
            return;
        }

        // HOLD CHANGE: bù sai lệch đồng hồ máy khách để thời gian hiển thị bám theo UTC của server.
        const startedAt = performance.now();

        const render = () => {
            const estimatedServerNow = serverNow + (performance.now() - startedAt);
            const remainingMilliseconds = Math.max(0, expiresAt - estimatedServerNow);
            const remainingSeconds = Math.ceil(remainingMilliseconds / 1000);
            const minutes = Math.floor(remainingSeconds / 60);
            const seconds = remainingSeconds % 60;

            output.textContent = `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
            timer.classList.toggle("is-urgent", remainingSeconds <= 60);

            if (remainingMilliseconds <= 0 && !redirected) {
                redirected = true;
                window.location.replace(expiredUrl);
            }
        };

        render();
        window.setInterval(render, 1000);
    });
})();
