(function () {
  const header = document.querySelector(".site-header, body > header");
  const progress = document.createElement("div");
  progress.className = "premium-progress";
  progress.setAttribute("aria-hidden", "true");
  document.body.appendChild(progress);

  let ticking = false;
  function updateScrollState() {
    const top = window.scrollY || document.documentElement.scrollTop;
    const range = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    progress.style.setProperty("--premium-progress", Math.min(1, top / range).toFixed(4));
    if (header) header.classList.toggle("is-scrolled", top > 18);
    ticking = false;
  }

  window.addEventListener("scroll", function () {
    if (ticking) return;
    ticking = true;
    window.requestAnimationFrame(updateScrollState);
  }, { passive: true });
  updateScrollState();

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const targets = document.querySelectorAll(
    ".course-card, .trust-item, .principle-card, .journey-step, .learning-card"
  );

  targets.forEach(function (element, index) {
    element.classList.add("premium-enter");
    element.style.setProperty("--premium-delay", `${Math.min(index % 4, 3) * 55}ms`);
  });

  if (reduceMotion || !("IntersectionObserver" in window)) {
    targets.forEach(function (element) { element.classList.add("is-visible"); });
    return;
  }

  const observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-visible");
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.08, rootMargin: "0px 0px -5% 0px" });

  targets.forEach(function (element) { observer.observe(element); });
})();
