document.querySelectorAll('[data-alert-duration]').forEach((alert) => {
    window.setTimeout(() => alert.remove(), Number(alert.dataset.alertDuration || 4000));
});
