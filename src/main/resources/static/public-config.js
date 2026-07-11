window.BROCA_ENROLLMENT_FORM_URL = "register.html";

(function () {
  const fallbackUrl = "register.html";

  function formUrl() {
    return String(window.BROCA_ENROLLMENT_FORM_URL || fallbackUrl).trim();
  }

  function isExternalUrl(url) {
    return /^https?:\/\//i.test(url);
  }

  function normalizePublicNavigation() {
    const nav = document.getElementById("primaryNav");
    if (!nav || nav.dataset.normalized === "true") return;

    const page = (window.location.pathname.split("/").pop() || "index.html").toLowerCase();
    const active = (...pages) => pages.includes(page) ? ' class="active"' : "";
    const onRegistrationPage = page === "register.html";
    const enrollmentAttributes = onRegistrationPage
      ? 'href="#registrationForm"'
      : 'href="register.html" data-enrollment-link';

    nav.innerHTML = `
      <ul>
        <li><a href="index.html"${active("index.html")}>首頁</a></li>
        <li><a href="about.html"${active("about.html")}>關於我們</a></li>
        <li class="dropdown">
          <a href="courses.html"${active("courses.html", "course-details.html")}>課程總覽 <i class="fa-solid fa-angle-down" aria-hidden="true"></i></a>
          <ul class="dropdown-menu">
            <li><a href="course-details.html?id=kindergarten">幼兒美語</a></li>
            <li><a href="course-details.html?id=halfday">幼兒小一銜接</a></li>
            <li><a href="course-details.html?id=kids">兒童美語</a></li>
            <li><a href="course-details.html?id=junior">國中先修</a></li>
            <li><a href="course-details.html?id=gept">GEPT 檢定</a></li>
            <li><a href="course-details.html?id=conversation">口說會話</a></li>
            <li><a href="course-details.html?id=tutor">安親課輔</a></li>
            <li><a href="course-details.html?id=senior">高中美語</a></li>
          </ul>
        </li>
        <li><a href="learning.html"${active("learning.html")}>學習中心</a></li>
        <li><a href="info.html#news">最新消息</a></li>
        <li><a href="info.html#calendar">家長行事曆</a></li>
        <li><a href="info.html#honor">榮譽榜</a></li>
        <li><a href="info.html#contact">聯絡我們</a></li>
        <li><a ${enrollmentAttributes} class="nav-cta nav-btn-experience">線上報名</a></li>
      </ul>`;
    nav.dataset.normalized = "true";
  }

  function applyEnrollmentLinks() {
    normalizePublicNavigation();
    const url = formUrl() || fallbackUrl;
    const external = isExternalUrl(url);

    document.querySelectorAll("[data-enrollment-link]").forEach(link => {
      link.href = url;
      if (external) {
        link.target = "_blank";
        link.rel = "noopener";
      } else {
        link.target = "_self";
        link.removeAttribute("rel");
      }
    });

    document.querySelectorAll("[data-enrollment-helper]").forEach(item => {
      item.textContent = "留下學生年級、英文程度與方便聯絡的時間，我們會協助安排合適的課程、試聽或課輔諮詢。";
    });

    const panel = document.querySelector(".registration-panel");
    if (!panel) return;

    const heading = panel.querySelector(".pane-head h2");
    const intro = panel.querySelector(".pane-head p");
    const copyTitle = panel.querySelector(".registration-copy h3");
    const button = panel.querySelector(".registration-button");

    if (heading) heading.textContent = "預約諮詢";
    if (intro) intro.textContent = "不確定適合哪個班也沒關係。先留下孩子的年級、程度與可上課時間，櫃台老師會再與您確認。";
    if (copyTitle) copyTitle.textContent = "讓我們先了解孩子，再安排合適的課程或試聽。";
    if (button) button.innerHTML = '<i class="fa-solid fa-pen-to-square"></i> 填寫諮詢資料';
  }

  window.BrocaEnrollment = { apply: applyEnrollmentLinks };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", applyEnrollmentLinks);
  } else {
    applyEnrollmentLinks();
  }
})();
