document.addEventListener("DOMContentLoaded", function () {
    const scriptLink = document.querySelector(".powershell-script-window-link");

    scriptLink.addEventListener("click", function (e) {
        e.preventDefault();
        window.open(e.href, "window", "width=900,height=640,resizable,scrollbars,toolbar,menubar");
    });
});
