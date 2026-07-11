const PUBLIC_NEWS_DATA = [
  {
    id: "summer-camp-2026",
    type: "notice",
    tag: "重要公告",
    title: "【2026 布魯卡美語暑期營隊｜報名開跑囉！】",
    date: "2026-06-22",
    desc: `布魯卡美語 2026 暑期營隊
以「快樂學習 × 多元探索 × 能力養成」為核心主軸，精心規劃適齡、分級、多元化課程，讓孩子在愉快的學習環境中培養能力、拓展視野。

八大主題探險，等你來挑戰：
這個夏天，孩子將透過昆蟲探索、生命教育、生活理財、烏克麗麗、食品科學、物理實驗、古文明探究與成果發表等多元主題，在觀察、體驗、合作與分享中，培養思考與解決問題的能力。

每天都有新發現，天天都有新收穫，讓孩子帶著滿滿的知識與歡笑，創造屬於自己的精彩暑假回憶。`,
    imgUrl: "2026-summer-camp.png"
  }
];

const PARENT_CALENDAR_EVENTS = [
  {
    date: "2026-01-01",
    type: "official",
    tag: "國定假日",
    title: "開國紀念日",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-02-15",
    type: "official",
    tag: "國定假日",
    title: "小年夜",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-02-16",
    type: "official",
    tag: "國定假日",
    title: "農曆除夕",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-02-17",
    type: "official",
    tag: "國定假日",
    title: "春節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-02-18",
    type: "official",
    tag: "國定假日",
    title: "春節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-02-19",
    type: "official",
    tag: "國定假日",
    title: "春節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-02-20",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 115 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2026-02-27",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 115 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2026-02-28",
    type: "official",
    tag: "國定假日",
    title: "和平紀念日",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-04-03",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 115 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2026-04-04",
    type: "official",
    tag: "國定假日",
    title: "兒童節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-04-05",
    type: "official",
    tag: "國定假日",
    title: "清明節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-04-06",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 115 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2026-05-01",
    type: "official",
    tag: "國定假日",
    title: "勞動節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-06-19",
    type: "official",
    tag: "國定假日",
    title: "端午節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-09-25",
    type: "official",
    tag: "國定假日",
    title: "中秋節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-09-28",
    type: "official",
    tag: "國定假日",
    title: "孔子誕辰紀念日 / 教師節",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-10-09",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 115 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2026-10-10",
    type: "official",
    tag: "國定假日",
    title: "國慶日",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-10-25",
    type: "official",
    tag: "國定假日",
    title: "臺灣光復暨金門古寧頭大捷紀念日",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2026-10-26",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 115 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2026-12-25",
    type: "official",
    tag: "國定假日",
    title: "行憲紀念日",
    note: "行政院人事行政總處 115 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-01-01",
    type: "official",
    tag: "國定假日",
    title: "開國紀念日",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-02-04",
    type: "official",
    tag: "國定假日",
    title: "小年夜",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-02-05",
    type: "official",
    tag: "國定假日",
    title: "農曆除夕",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-02-06",
    type: "official",
    tag: "國定假日",
    title: "春節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-02-07",
    type: "official",
    tag: "國定假日",
    title: "春節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-02-08",
    type: "official",
    tag: "國定假日",
    title: "春節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-02-09",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-02-10",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-02-28",
    type: "official",
    tag: "國定假日",
    title: "和平紀念日",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-03-01",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-04-04",
    type: "official",
    tag: "國定假日",
    title: "兒童節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-04-05",
    type: "official",
    tag: "國定假日",
    title: "清明節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-04-06",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-04-30",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-05-01",
    type: "official",
    tag: "國定假日",
    title: "勞動節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-06-09",
    type: "official",
    tag: "國定假日",
    title: "端午節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-09-15",
    type: "official",
    tag: "國定假日",
    title: "中秋節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-09-28",
    type: "official",
    tag: "國定假日",
    title: "孔子誕辰紀念日 / 教師節",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-10-10",
    type: "official",
    tag: "國定假日",
    title: "國慶日",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-10-11",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-10-25",
    type: "official",
    tag: "國定假日",
    title: "臺灣光復暨金門古寧頭大捷紀念日",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-12-24",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  },
  {
    date: "2027-12-25",
    type: "official",
    tag: "國定假日",
    title: "行憲紀念日",
    note: "行政院人事行政總處 116 年辦公日曆標示為放假日。",
    action: "官方放假日"
  },
  {
    date: "2027-12-31",
    type: "makeup",
    tag: "補假",
    title: "補假",
    note: "行政院人事行政總處 116 年辦公日曆標示為補假。",
    action: "官方補假日"
  }
];

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function initPublicMenu() {
  const toggle = document.getElementById("menuToggle");
  const nav = document.getElementById("primaryNav");
  if (!toggle || !nav) return;

  function setOpen(open) {
    nav.classList.toggle("open", open);
    document.body.classList.toggle("menu-open", open);
    toggle.setAttribute("aria-expanded", open ? "true" : "false");
    toggle.setAttribute("aria-label", open ? "關閉選單" : "開啟選單");
    toggle.innerHTML = open ? '<i class="fa-solid fa-xmark"></i>' : '<i class="fa-solid fa-bars"></i>';
  }

  toggle.addEventListener("click", () => setOpen(!nav.classList.contains("open")));
  nav.querySelectorAll("a").forEach(link => {
    link.addEventListener("click", () => {
      if (window.innerWidth <= 900 && !link.closest(".dropdown")) setOpen(false);
    });
  });
  window.addEventListener("keydown", event => {
    if (event.key === "Escape") setOpen(false);
  });
  window.addEventListener("resize", () => {
    if (window.innerWidth > 900) setOpen(false);
  });
}

function initReveal() {
  const items = document.querySelectorAll(".reveal");
  if (!items.length) return;

  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    items.forEach(item => item.classList.add("is-visible"));
    return;
  }

  const observer = new IntersectionObserver((entries, obs) => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-visible");
      obs.unobserve(entry.target);
    });
  }, { threshold: 0.12 });

  items.forEach((item, index) => {
    item.style.transitionDelay = `${Math.min((index % 4) * 0.06, 0.18)}s`;
    observer.observe(item);
  });
}

function renderNewsList(container) {
  if (!PUBLIC_NEWS_DATA.length) {
    container.innerHTML = `
      <div class="empty-state">
        <i class="fa-regular fa-newspaper"></i>
        <h3>最新內容即將更新</h3>
        <p>活動、招生與課程公告會陸續整理於此。</p>
      </div>`;
    return;
  }

  container.innerHTML = `
    <div class="news-list">
      ${PUBLIC_NEWS_DATA.map(item => {
        const [, month, day] = item.date.split("-");
        const summary = item.desc.replace(/\s+/g, " ").slice(0, 92);
        return `
          <a class="news-item" href="info.html?newsId=${encodeURIComponent(item.id)}">
            <div class="date-box">
              <strong>${escapeHtml(day)}</strong>
              <span>${escapeHtml(month)} 月</span>
            </div>
            <div class="news-text">
              <span class="badge">${escapeHtml(item.tag)}</span>
              <h3>${escapeHtml(item.title)}</h3>
              <p>${escapeHtml(summary)}...</p>
            </div>
          </a>`;
      }).join("")}
    </div>`;
  window.BrocaEnrollment?.apply();
}

function renderNewsArticle(container, article) {
  const image = article.imgUrl
    ? `<img class="article-image" src="${escapeHtml(article.imgUrl)}" alt="${escapeHtml(article.title)}">`
    : "";

  container.innerHTML = `
    <a href="info.html#news" class="back-link"><i class="fa-solid fa-arrow-left"></i> 返回消息列表</a>
    <div class="pane-card">
      <div class="pane-head">
        <span class="badge">${escapeHtml(article.tag)}</span>
        <h2>${escapeHtml(article.title)}</h2>
        <p><i class="fa-regular fa-calendar" style="margin-right: 6px;"></i>${escapeHtml(article.date)}</p>
      </div>
      <div class="pane-body">
        <div class="article-body">${escapeHtml(article.desc)}</div>
        <div class="article-actions">
          <a href="info.html#registration" class="btn btn-primary" data-enrollment-link><i class="fa-solid fa-pen-to-square"></i> 線上報名</a>
          <a href="info.html#contact" class="btn btn-quiet"><i class="fa-solid fa-phone"></i> 聯絡詢問</a>
        </div>
        ${image}
      </div>
    </div>`;
  window.BrocaEnrollment?.apply();
}

function parseLocalDate(dateText) {
  const [year, month, day] = dateText.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function formatCalendarDate(dateText) {
  const date = parseLocalDate(dateText);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const weekday = ["日", "一", "二", "三", "四", "五", "六"][date.getDay()];
  return { month, day, weekday, full: `${month}/${day}（週${weekday}）` };
}

function calendarTypeLabel(type) {
  if (type === "makeup") return "官方補假";
  return "國定假日";
}

function rocYearLabel(year) {
  return `${Number(year) - 1911} 年`;
}

const FAMILY_CALENDAR_STATE = {
  year: "upcoming",
  month: "all"
};
const UPCOMING_EVENT_LIMIT = 6;

function renderCalendarEvent(event) {
  const date = formatCalendarDate(event.date);
  const typeLabel = calendarTypeLabel(event.type);
  return `
    <article class="calendar-event calendar-event-${escapeHtml(event.type)}">
      <div class="calendar-date">
        <strong>${escapeHtml(date.day)}</strong>
        <span>${escapeHtml(date.month)} 月</span>
      </div>
      <div class="calendar-event-main">
        <div class="calendar-event-meta">
          <span>${escapeHtml(typeLabel)}</span>
          <span>${escapeHtml(date.full)}</span>
        </div>
        <h3>${escapeHtml(event.title)}</h3>
        <p>${escapeHtml(event.note)}</p>
      </div>
      <span class="calendar-event-tag">${escapeHtml(event.tag)}</span>
    </article>`;
}

function updateCalendarControls() {
  document.querySelectorAll("[data-calendar-year]").forEach(button => {
    button.classList.toggle("active", button.dataset.calendarYear === FAMILY_CALENDAR_STATE.year);
  });

  const monthFilter = document.getElementById("calendarMonthFilter");
  if (!monthFilter) return;
  monthFilter.disabled = FAMILY_CALENDAR_STATE.year === "upcoming";
  if (monthFilter.disabled) {
    FAMILY_CALENDAR_STATE.month = "all";
  }
  monthFilter.value = FAMILY_CALENDAR_STATE.month;
}

function renderFamilyCalendar() {
  const list = document.getElementById("familyCalendarEvents");
  const nextCard = document.getElementById("nextFamilyEvent");
  const viewTitle = document.getElementById("calendarViewTitle");
  const viewMeta = document.getElementById("calendarViewMeta");
  if (!list || !nextCard) return;

  const todayRaw = new Date();
  const today = new Date(todayRaw.getFullYear(), todayRaw.getMonth(), todayRaw.getDate());
  const events = [...PARENT_CALENDAR_EVENTS].sort((a, b) => parseLocalDate(a.date) - parseLocalDate(b.date));
  const upcoming = events.find(event => parseLocalDate(event.date) >= today) || events[events.length - 1];

  if (upcoming) {
    const date = formatCalendarDate(upcoming.date);
    nextCard.innerHTML = `
      <span class="mini-label">Next Holiday</span>
      <div class="next-date">${escapeHtml(date.full)}</div>
      <h3>${escapeHtml(upcoming.title)}</h3>
      <p>${escapeHtml(upcoming.action)}</p>`;
  }

  updateCalendarControls();

  let shownEvents = [];
  if (FAMILY_CALENDAR_STATE.year === "upcoming") {
    shownEvents = events.filter(event => parseLocalDate(event.date) >= today).slice(0, UPCOMING_EVENT_LIMIT);
    if (!shownEvents.length) {
      shownEvents = events.slice(-UPCOMING_EVENT_LIMIT);
    }
    if (viewTitle) viewTitle.textContent = "接下來的國定假日";
    if (viewMeta) viewMeta.textContent = `顯示最近 ${shownEvents.length} 筆重點，想查全年可切換 2026 或 2027。`;
  } else {
    shownEvents = events.filter(event => {
      const matchesYear = event.date.startsWith(FAMILY_CALENDAR_STATE.year);
      const matchesMonth = FAMILY_CALENDAR_STATE.month === "all" || event.date.slice(5, 7) === FAMILY_CALENDAR_STATE.month;
      return matchesYear && matchesMonth;
    });
    const monthText = FAMILY_CALENDAR_STATE.month === "all" ? "全年" : `${Number(FAMILY_CALENDAR_STATE.month)} 月`;
    if (viewTitle) viewTitle.textContent = `${FAMILY_CALENDAR_STATE.year} 國定假日`;
    if (viewMeta) viewMeta.textContent = `${monthText}共有 ${shownEvents.length} 筆國定假日與補假。`;
  }

  if (!shownEvents.length) {
    list.innerHTML = `
      <div class="empty-state">
        <i class="fa-regular fa-calendar"></i>
        <h3>此篩選沒有假日資料</h3>
        <p>請切換年份或月份查看其他國定假日。</p>
      </div>`;
    return;
  }

  const groupedByMonth = shownEvents.reduce((groups, event) => {
    const key = event.date.slice(0, 7);
    groups[key] = groups[key] || [];
    groups[key].push(event);
    return groups;
  }, {});

  list.innerHTML = Object.entries(groupedByMonth).map(([monthKey, monthEvents]) => {
    const [year, month] = monthKey.split("-");
    return `
    <section class="calendar-year-group" aria-label="${escapeHtml(year)} 年 ${escapeHtml(month)} 月國定假日">
      <div class="calendar-year-head">
        <span>${escapeHtml(Number(month))} 月</span>
        <small>${escapeHtml(year)}・${escapeHtml(rocYearLabel(year))}</small>
      </div>
      <div class="calendar-year-events">
        ${monthEvents.map(renderCalendarEvent).join("")}
      </div>
    </section>`;
  }).join("");
}

function setupFamilyCalendarControls() {
  document.querySelectorAll("[data-calendar-year]").forEach(button => {
    button.addEventListener("click", () => {
      FAMILY_CALENDAR_STATE.year = button.dataset.calendarYear || "upcoming";
      if (FAMILY_CALENDAR_STATE.year === "upcoming") {
        FAMILY_CALENDAR_STATE.month = "all";
      }
      renderFamilyCalendar();
    });
  });

  const monthFilter = document.getElementById("calendarMonthFilter");
  if (monthFilter) {
    monthFilter.addEventListener("change", () => {
      FAMILY_CALENDAR_STATE.month = monthFilter.value || "all";
      renderFamilyCalendar();
    });
  }
}

function initInfoHeroEffects() {
  const card = document.querySelector("[data-info-hero-card]");
  if (!card) return;

  const canUseFineMotion = window.matchMedia("(pointer: fine)").matches
    && !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!canUseFineMotion) return;

  function resetCard() {
    card.style.setProperty("--tilt-x", "0deg");
    card.style.setProperty("--tilt-y", "0deg");
    card.style.setProperty("--shine-shift", "0px");
  }

  card.addEventListener("pointermove", event => {
    const rect = card.getBoundingClientRect();
    const x = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
    const y = Math.min(Math.max((event.clientY - rect.top) / rect.height, 0), 1);
    const rotateX = (0.5 - y) * 4;
    const rotateY = (x - 0.5) * 4;
    const shineShift = (x - 0.5) * 72;

    card.style.setProperty("--tilt-x", `${rotateX.toFixed(2)}deg`);
    card.style.setProperty("--tilt-y", `${rotateY.toFixed(2)}deg`);
    card.style.setProperty("--shine-shift", `${shineShift.toFixed(1)}px`);
  });

  card.addEventListener("pointerleave", resetCard);
}

function switchInfoPane(paneId, updateHash = true, scrollIntoView = false) {
  document.querySelectorAll(".info-pane").forEach(pane => {
    pane.classList.toggle("active", pane.id === paneId);
  });
  document.querySelectorAll(".info-tab").forEach(tab => {
    tab.classList.toggle("active", tab.dataset.infoTab === paneId);
  });
  document.querySelectorAll(".site-nav a[href*='info.html#']").forEach(link => {
    const target = link.getAttribute("href").split("#")[1];
    if (target && !link.classList.contains("nav-cta")) {
      link.classList.toggle("active", target === paneId);
    }
  });
  if (updateHash) history.replaceState(null, "", `#${paneId}`);

  if (scrollIntoView) {
    requestAnimationFrame(() => {
      document.getElementById(paneId)?.scrollIntoView({ block: "start" });
    });
  }
}

function initInfoPage() {
  const newsContainer = document.getElementById("newsDynamicContent");
  if (!newsContainer) return;

  const params = new URLSearchParams(window.location.search);
  const newsId = params.get("newsId");
  const article = PUBLIC_NEWS_DATA.find(item => item.id === newsId);

  if (newsId && article) {
    renderNewsArticle(newsContainer, article);
    switchInfoPane("news", false);
  } else {
    renderNewsList(newsContainer);
  }

  document.querySelectorAll(".info-tab").forEach(tab => {
    tab.addEventListener("click", () => switchInfoPane(tab.dataset.infoTab));
  });

  document.querySelectorAll("[data-info-jump]").forEach(link => {
    link.addEventListener("click", event => {
      event.preventDefault();
      switchInfoPane(link.dataset.infoJump, true, true);
    });
  });

  setupFamilyCalendarControls();
  renderFamilyCalendar();

  const hash = window.location.hash.replace("#", "");
  if (hash && document.getElementById(hash)) switchInfoPane(hash, false, true);

  window.addEventListener("hashchange", () => {
    const next = window.location.hash.replace("#", "");
    if (next && document.getElementById(next)) switchInfoPane(next, false, true);
  });
}

document.addEventListener("DOMContentLoaded", () => {
  initPublicMenu();
  initReveal();
  initInfoHeroEffects();
  initInfoPage();
});
