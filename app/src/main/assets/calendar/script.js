// State
let today = new Date();
let currentMonth = today.getMonth();
let currentYear = today.getFullYear();
let selectedCell = null;

// Persistence helpers
function loadEvents() {
    const raw = localStorage.getItem("calendarEvents");
    return raw ? JSON.parse(raw) : [];
}
function saveEvents(arr) {
    localStorage.setItem("calendarEvents", JSON.stringify(arr));
}

// Events array (persisted)
let events = loadEvents();

// Utility: format YYYY-MM-DD
function toIso(year, monthIndex, day) {
    return `${year}-${String(monthIndex+1).padStart(2,"0")}-${String(day).padStart(2,"0")}`;
}

// Render calendar
function renderCalendar(year, month) {
    const calendarDays = document.querySelector(".calendar-days");
    const currentMonthLabel = document.querySelector(".current-month");
    calendarDays.innerHTML = "";

    currentMonthLabel.textContent = new Intl.DateTimeFormat("uk-UA", {month:"long", year:"numeric"}).format(new Date(year, month, 1));

    const firstDay = new Date(year, month, 1).getDay();
    const totalDays = new Date(year, month + 1, 0).getDate();
    const prevMonthDays = new Date(year, month, 0).getDate();

    // Previous month padding
    for (let i = 0; i < firstDay; i++) {
        const pad = document.createElement("div");
        pad.className = "padding-day";
        pad.textContent = prevMonthDays - firstDay + i + 1;
        calendarDays.appendChild(pad);
    }

    // Current month days
    for (let d = 1; d <= totalDays; d++) {
        const dayCell = document.createElement("div");
        dayCell.className = "month-day";
        dayCell.setAttribute("role","button");
        dayCell.setAttribute("tabindex","0");
        dayCell.textContent = d;

        // mark today
        if (d === today.getDate() && month === today.getMonth() && year === today.getFullYear()) {
            dayCell.classList.add("current-day");
        }

        // add event dot if event exists
        const iso = toIso(year, month, d);
        const dayEvents = events.filter(e => e.date === iso);
        if (dayEvents.length) {
            const dot = document.createElement("div");
            dot.className = "event-dot";
            dayCell.appendChild(dot);
        }

        // click/select handler
        dayCell.addEventListener("click", () => {
            if (selectedCell) selectedCell.classList.remove("selected");
            dayCell.classList.add("selected");
            selectedCell = dayCell;
            openDayEvents(iso);
        });

        // keyboard support (Enter/Space)
        dayCell.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                dayCell.click();
            }
        });

        calendarDays.appendChild(dayCell);
    }
    

    // Next month padding to complete the last week
    const totalCells = calendarDays.children.length;
    const extra = (7 - (totalCells % 7)) % 7;
    for (let i = 1; i <= extra; i++) {
        const pad = document.createElement("div");
        pad.className = "padding-day";
        pad.textContent = i;
        calendarDays.appendChild(pad);
    }
}

// Modal and form wiring
const modalBackdrop = document.getElementById("modalBackdrop");
const eventForm = document.getElementById("eventForm");
const eventDateInput = document.getElementById("eventDate");
const eventTitleInput = document.getElementById("eventTitle");
const openAddEventBtn = document.getElementById("openAddEvent");
const cancelEventBtn = document.getElementById("cancelEvent");
const dayEventsContainer = document.getElementById("dayEventsContainer");
const eventList = document.getElementById("eventList");
const modalTitle = document.getElementById("modalTitle");
const dayLabel = document.getElementById("dayLabel");
const closeDayEventsBtn = document.getElementById("closeDayEvents");
let editingEvent = null;

function openModal() {
    modalBackdrop.style.display = "flex";
    modalBackdrop.setAttribute("aria-hidden", "false");
    eventDateInput.focus();
}
function closeModal() {
    modalBackdrop.style.display = "none";
    modalBackdrop.setAttribute("aria-hidden", "true");
    // reset form and view
    eventForm.style.display = "block";
    dayEventsContainer.style.display = "none";
    eventForm.reset();
    editingEvent = null;
}

openAddEventBtn.addEventListener("click", () => {
    modalTitle.textContent = "Додати подію";
    editingEvent = null;
    eventForm.style.display = "block";
    dayEventsContainer.style.display = "none";
    // default date to today in current view
    const defaultDay = today.getFullYear() === currentYear && today.getMonth() === currentMonth ? today.getDate() : 1;
    eventDateInput.value = toIso(currentYear, currentMonth, defaultDay);
    eventTitleInput.value = "";
    openModal();
});

cancelEventBtn.addEventListener("click", closeModal);
modalBackdrop.addEventListener("click", (e) => {
    if (e.target === modalBackdrop) closeModal();
});

// Add or update event
eventForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const date = eventDateInput.value; // YYYY-MM-DD
    const title = eventTitleInput.value.trim();
    if (!date || !title) return;
    // push new event
    if (editingEvent) Object.assign(editingEvent, {date, title});
    else events.push({ date, title });
    saveEvents(events);
    closeModal();
    renderCalendar(currentYear, currentMonth);
});

// Open day events view
function openDayEvents(isoDate) {
    modalTitle.textContent = "Події за " + isoDate;
    eventForm.style.display = "none";
    dayEventsContainer.style.display = "block";
    eventList.innerHTML = "";

    const dayEvents = events.filter(e => e.date === isoDate);
    if (dayEvents.length === 0) {
        eventList.innerHTML = '<div style="padding:8px;color:#666">Подій немає</div>';
    } else {
        dayEvents.forEach((ev, idx) => {
            const row = document.createElement("div");
            row.className = "event-item";
            const title = document.createElement("div");
            title.className = "event-title";
            title.textContent = ev.title;
            const controls = document.createElement("div");
            controls.className = "event-controls";

            const editBtn = document.createElement("button");
            editBtn.title = "Редагувати";
            editBtn.textContent = '✎';
            editBtn.addEventListener("click", () => {
                // populate form for edit
                modalTitle.textContent = "Редагувати подію";
                eventForm.style.display = "block";
                dayEventsContainer.style.display = "none";
                eventDateInput.value = ev.date;
                eventTitleInput.value = ev.title;
                editingEvent = ev;
            });

            const delBtn = document.createElement("button");
            delBtn.title = "Видалити";
            delBtn.textContent = '×';
            delBtn.addEventListener("click", () => {
                // remove event
                events.splice(events.indexOf(ev), 1);
                saveEvents(events);
                openDayEvents(isoDate); // refresh list
                renderCalendar(currentYear, currentMonth);
            });

            controls.appendChild(editBtn);
            controls.appendChild(delBtn);
            row.appendChild(title);
            row.appendChild(controls);
            eventList.appendChild(row);
        });
    }

    openModal();
}

closeDayEventsBtn.addEventListener("click", closeModal);

// Navigation helpers
function goToPrevMonth() {
    currentMonth--;
    if (currentMonth < 0) { currentMonth = 11; currentYear--; }
    renderCalendar(currentYear, currentMonth);
}
function goToNextMonth() {
    currentMonth++;
    if (currentMonth > 11) { currentMonth = 0; currentYear++; }
    renderCalendar(currentYear, currentMonth);
}
function goToPrevYear() {
    currentYear--;
    renderCalendar(currentYear, currentMonth);
}
function goToNextYear() {
    currentYear++;
    renderCalendar(currentYear, currentMonth);
}
function goToToday() {
    today = new Date();
    currentMonth = today.getMonth();
    currentYear = today.getFullYear();
    renderCalendar(currentYear, currentMonth);
}

// Wire buttons
document.querySelector(".prev").addEventListener("click", goToPrevMonth);
document.querySelector(".next").addEventListener("click", goToNextMonth);
document.querySelector(".prev-year").addEventListener("click", goToPrevYear);
document.querySelector(".next-year").addEventListener("click", goToNextYear);
document.querySelector(".today").addEventListener("click", goToToday);

// Keyboard shortcuts: left/right = month, up/down = year, t = today, a = add event
document.addEventListener("keydown", (e) => {
    if (e.target.tagName === "INPUT" || e.target.isContentEditable) return;
    if (e.key === "ArrowLeft") goToPrevMonth();
    if (e.key === "ArrowRight") goToNextMonth();
    if (e.key === "ArrowUp") goToPrevYear();
    if (e.key === "ArrowDown") goToNextYear();
    if (e.key.toLowerCase() === "t") goToToday();
    if (e.key.toLowerCase() === "a") openAddEventBtn.click();
});

// Initial render
renderCalendar(currentYear, currentMonth);
  
