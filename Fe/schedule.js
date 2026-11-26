document.addEventListener("DOMContentLoaded", () => {
  const $ = (id) => document.getElementById(id);
  const API = window.SMARTEDU_API_BASE_URL || "http://localhost:8080";
  const days = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
  const colors = ["ev-blue", "ev-purple", "ev-green", "ev-teal", "ev-amber", "ev-pink"];
  let semesters = [];
  let semester = null;
  let courses = [];
  let catalogSections = [];
  let selectedCourseIds = new Set();
  let planId = null;
  let passedRecords = [];
  let prerequisiteCheck = null;
  let options = [];
  let activeOptionId = null;
  let weeklySchedule = null;
  let scheduleRange = null;
  let activeScheduleSemesterId = null;
  let savedSchedules = [];
  let selectedForCompare = new Set();
  let weekIndex = 0;
  let editingSection = null;
  let scheduleBusy = false;
  let scheduleSource = "catalog";
  let manualCourses = readManualCourses();
  let selectedManualCourseIds = new Set();
  let manualDraft = null;
  let manualEditingId = null;
  let manualOptionSequence = 0;
  const manualStorageKey = "smartedu_manual_schedule_courses";
  const periodClock = ["07:00", "07:50", "08:50", "09:40", "10:40", "11:30", "13:00", "13:50", "14:50", "15:40", "16:40", "17:30", "18:20"];

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, ch => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    }[ch]));
  }

  function toast(message) {
    const el = $("toast");
    if (!el) return;
    $("toastTxt").textContent = message;
    el.classList.add("show");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => el.classList.remove("show"), 3200);
  }

  async function api(path, options = {}) {
    const token = localStorage.getItem("smartedu_access_token");
    const headers = { ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }), ...(options.headers || {}) };
    if (token) headers.Authorization = "Bearer " + token;
    const response = await fetch(API + path, { ...options, headers });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401) {
        localStorage.removeItem("smartedu_access_token");
        localStorage.removeItem("smartedu_user");
        window.location.replace("super_index.html");
      }
      const fields = Array.isArray(payload.fieldErrors)
        ? payload.fieldErrors.map(item => `${item.field}: ${item.message}`).join("\n") : "";
      throw new Error(fields || payload.message || "Không thể kết nối với máy chủ.");
    }
    return Object.prototype.hasOwnProperty.call(payload, "data") ? payload.data : payload;
  }

  async function downloadExport(format) {
    if (!activeOptionId) return toast("Hãy chọn một phương án thời khóa biểu trước.");
    const option = activeOption();
    if (option?.manual) {
      if (format === "PDF") return printManualSchedule();
      if (format === "PNG") return downloadManualPng();
    }
    const token = localStorage.getItem("smartedu_access_token");
    const response = await fetch(API + "/api/schedules/export", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}) },
      body: JSON.stringify({ optionId: activeOptionId, format })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      if (response.status === 401) window.location.replace("super_index.html");
      throw new Error(body.message || "Không thể xuất thời khóa biểu.");
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `thoi-khoa-bieu.${format.toLowerCase()}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  function printManualSchedule() {
    const meetings = weeklySchedule?.meetings || [];
    const popup = window.open("", "_blank", "width=1200,height=850");
    if (!popup) return toast("Cho phép cửa sổ in để lưu lịch nhập tay thành PDF.");
    const rows = meetings.map(meeting => `<tr><td>${esc(weekDayLabel(meeting.weekday))}</td><td>${esc(meeting.startTime)}–${esc(meeting.endTime)}</td><td>${esc(meeting.courseCode)} · ${esc(meeting.courseName)}</td><td>${esc(meeting.sectionCode)} · ${esc(meeting.className)}</td><td>${esc(meeting.instructorName)}</td><td>${esc(meeting.roomName)}${meeting.campusName ? ` · ${esc(meeting.campusName)}` : ""}</td></tr>`).join("");
    popup.document.write(`<!doctype html><html lang="vi"><meta charset="utf-8"><title>${esc(activeOption()?.optionName || "Thời khóa biểu")}</title><style>body{font:14px Arial,sans-serif;color:#20283b;padding:28px}h1{font-size:22px}p{color:#56617a}table{width:100%;border-collapse:collapse}th,td{padding:9px;border:1px solid #d9deea;text-align:left;vertical-align:top}th{background:#f2f1ff}@media print{body{padding:0}}</style><h1>${esc(activeOption()?.optionName || "Thời khóa biểu nhập tay")}</h1><p>${esc(activeOption()?.totalCredits)} tín chỉ · ${esc(activeOption()?.totalScore)} điểm · Lịch lặp hàng tuần</p><table><thead><tr><th>Ngày</th><th>Giờ học</th><th>Học phần</th><th>Lớp</th><th>Giảng viên</th><th>Phòng</th></tr></thead><tbody>${rows}</tbody></table></html>`);
    popup.document.close();
    popup.focus();
    setTimeout(() => popup.print(), 250);
  }

  async function downloadManualPng() {
    const meetings = weeklySchedule?.meetings || [];
    if (!meetings.length) return toast("Chưa có dữ liệu để xuất ảnh.");
    const canvas = document.createElement("canvas");
    canvas.width = 1800; canvas.height = 1320;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = "#182137"; ctx.font = "bold 32px Arial"; ctx.fillText(activeOption()?.optionName || "Thời khóa biểu", 48, 55);
    ctx.font = "18px Arial"; ctx.fillStyle = "#68738b"; ctx.fillText(`${activeOption()?.totalCredits} tín chỉ · ${activeOption()?.totalScore} điểm · Lịch lặp hàng tuần`, 48, 88);
    const x0 = 130, y0 = 135, cellW = 235, cellH = 93;
    ctx.textAlign = "center"; ctx.font = "bold 17px Arial"; ctx.fillStyle = "#4b5570";
    days.forEach((day, index) => ctx.fillText(day, x0 + index * cellW + cellW / 2, y0 - 14));
    for (let period = 1; period <= 12; period++) {
      const y = y0 + (period - 1) * cellH;
      ctx.fillStyle = "#fcfcff"; ctx.fillRect(0, y, x0, cellH);
      ctx.fillStyle = "#273047"; ctx.font = "bold 14px Arial"; ctx.textAlign = "center";
      ctx.fillText(`Tiết ${period}`, 64, y + 45);
      ctx.strokeStyle = "#dfe2ec"; ctx.strokeRect(0, y, x0, cellH);
      for (let day = 1; day <= 7; day++) {
        const x = x0 + (day - 1) * cellW;
        ctx.strokeStyle = "#dfe2ec"; ctx.strokeRect(x, y, cellW, cellH);
        const meeting = meetings.find(item => Number(item.weekday) === day && Number(item.startPeriod) === period);
        if (!meeting) continue;
        const span = Math.max(1, Number(meeting.endPeriod) - Number(meeting.startPeriod) + 1);
        const cardHeight = cellH * span - 8;
        const palette = ["#dbeafe", "#e9e5ff", "#dcfce7", "#ccfbf1", "#fef3c7", "#fce7f3"];
        const hash = String(meeting.courseId).split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
        ctx.fillStyle = palette[hash % palette.length]; ctx.fillRect(x + 4, y + 4, cellW - 8, cardHeight);
        ctx.fillStyle = "#20283b"; ctx.textAlign = "left"; ctx.font = "bold 11px Arial";
        ctx.fillText(`${meeting.courseCode} · ${meeting.sectionCode} · ${meeting.className || ""}`, x + 10, y + 21, cellW - 20);
        ctx.font = "11px Arial"; ctx.fillText(String(meeting.courseName), x + 10, y + 37, cellW - 20);
        ctx.fillText(`${meeting.startTime}–${meeting.endTime}`, x + 10, y + 53, cellW - 20);
        ctx.fillText(`${meeting.roomName}${meeting.campusName ? ` · ${meeting.campusName}` : ""}`, x + 10, y + 69, cellW - 20);
        ctx.fillText(String(meeting.instructorName), x + 10, y + 85, cellW - 20);
      }
    }
    const blob = await new Promise(resolve => canvas.toBlob(resolve, "image/png"));
    if (!blob) return toast("Không thể tạo ảnh trên trình duyệt này.");
    const url = URL.createObjectURL(blob), link = document.createElement("a");
    link.href = url; link.download = "thoi-khoa-bieu-nhap-tay.png"; link.click();
    URL.revokeObjectURL(url);
  }

  function setStatus(title, detail, error = false) {
    $("scheduleStatusTitle").textContent = title;
    $("scheduleStatusText").textContent = detail;
    $("validStat").textContent = "";
    $("scheduleStatusTitle").closest(".valid-banner").classList.toggle("status-error", error);
  }

  function setBusy(button, busy, busyText = "Đang xử lý...") {
    if (!button) return;
    if (busy) {
      button.dataset.originalHtml = button.innerHTML;
      button.disabled = true;
      button.textContent = busyText;
    } else {
      button.disabled = false;
      if (button.dataset.originalHtml) button.innerHTML = button.dataset.originalHtml;
    }
  }

  function readManualCourses() {
    try {
      const saved = JSON.parse(localStorage.getItem("smartedu_manual_schedule_courses") || "[]");
      return Array.isArray(saved) ? saved : [];
    } catch (_) { return []; }
  }

  function persistManualCourses() {
    localStorage.setItem(manualStorageKey, JSON.stringify(manualCourses));
  }

  function newLocalId(prefix) {
    return `${prefix}-${window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
  }

  function weekDayLabel(value) {
    return days[Number(value) - 1] || `Thứ ${value}`;
  }

  function periodToTime(period, end = false) {
    const n = Number(period);
    return periodClock[Math.max(0, Math.min(12, n - (end ? 0 : 1)))] || "";
  }

  function formatMeetingTime(meeting) {
    const start = meeting.startTime || periodToTime(meeting.startPeriod);
    const end = meeting.endTime || periodToTime(meeting.endPeriod, true);
    return `${start}–${end} · tiết ${meeting.startPeriod}–${meeting.endPeriod}`;
  }

  function currentManualSelection() {
    return manualCourses.filter(course => selectedManualCourseIds.has(course.localId));
  }

  function switchScheduleSource(source) {
    scheduleSource = source;
    $("sourceCatalog").classList.toggle("active", source === "catalog");
    $("sourceManual").classList.toggle("active", source === "manual");
    $("sourceCatalog").setAttribute("aria-selected", String(source === "catalog"));
    $("sourceManual").setAttribute("aria-selected", String(source === "manual"));
    $("catalogPanel").hidden = source !== "catalog";
    $("manualPanel").hidden = source !== "manual";
    renderStep1();
    setStatus(source === "catalog" ? "Danh mục SmartEdu" : "Nguồn học phần thủ công",
      source === "catalog" ? "Chọn học phần trong danh mục của học kỳ đã tải từ Backend." : "Chọn học phần Excel hoặc tự nhập. Các tổ hợp sẽ được kiểm tra trên trình duyệt.");
  }

  function renderManualCourses() {
    const query = $("manualCourseSearch").value.trim().toLocaleLowerCase("vi");
    const filtered = manualCourses.filter(course => `${course.courseCode} ${course.courseName}`.toLocaleLowerCase("vi").includes(query));
    $("manualCourseList").innerHTML = filtered.length ? filtered.map(course => `
      <div class="manual-course-row" data-manual-course="${esc(course.localId)}">
        <input type="checkbox" class="c-chk" data-manual-select="${esc(course.localId)}" ${selectedManualCourseIds.has(course.localId) ? "checked" : ""} aria-label="Chọn ${esc(course.courseCode)}">
        <span class="c-bar" style="background:var(--primary)"></span>
        <div class="manual-course-main">
          <div class="manual-course-title"><b>${esc(course.courseCode)} · ${esc(course.courseName)}</b><small>${esc(course.credits)} tín chỉ · ${(course.sections || []).length} lớp</small></div>
          <div class="manual-section-list">${(course.sections || []).map(section => {
            const schedule = (section.meetings || []).map(meeting => `${weekDayLabel(meeting.weekday)} ${formatMeetingTime(meeting)}`).join("; ");
            return `<div class="manual-section-summary"><b>${esc(section.sectionCode)}</b>${section.className ? ` · ${esc(section.className)}` : ""} · ${esc(schedule || "Chưa có lịch")} · ${esc(section.instructorName || "Chưa có giảng viên")} · ${esc(section.roomName || "Chưa có phòng")}${section.campusName ? ` · ${esc(section.campusName)}` : ""}</div>`;
          }).join("")}</div>
        </div>
        <div class="manual-course-actions"><button type="button" data-edit-manual="${esc(course.localId)}">Sửa</button><button type="button" data-delete-manual="${esc(course.localId)}">Xóa</button></div>
      </div>`).join("") : `<div class="empty-state">${manualCourses.length ? "Không tìm thấy học phần." : "Chưa có dữ liệu. Nhập tệp Excel/CSV hoặc thêm học phần thủ công."}</div>`;
  }

  function blankManualMeeting() { return { weekday: 2, startPeriod: 1, endPeriod: 3 }; }
  function blankManualSection(index = 1) {
    return { sectionCode: `LHP-${index}`, className: `Lớp ${index}`, instructorName: "", roomName: "", campusName: "", meetings: [blankManualMeeting()] };
  }

  function renderManualEditor() {
    if (!manualDraft) return;
    $("manualCourseTitle").textContent = manualEditingId ? "Sửa học phần thủ công" : "Thêm học phần thủ công";
    $("manualCourseCode").value = manualDraft.courseCode || "";
    $("manualCourseName").value = manualDraft.courseName || "";
    $("manualCourseCredits").value = manualDraft.credits || 3;
    const dayOptions = days.map((label, index) => `<option value="${index + 1}">${label}</option>`).join("");
    $("manualSectionList").innerHTML = (manualDraft.sections || []).map((section, sectionIndex) => `
      <div class="manual-section-editor" data-section-index="${sectionIndex}">
        <div class="manual-section-editor-head"><b>Lớp học phần ${sectionIndex + 1}</b><button type="button" data-remove-manual-section="${sectionIndex}">Xóa lớp</button></div>
        <div class="manual-section-fields">
          <div><label>Mã lớp học phần</label><input data-section-field="sectionCode" value="${esc(section.sectionCode)}" placeholder="CS101.01"></div>
          <div><label>Tên lớp</label><input data-section-field="className" value="${esc(section.className)}" placeholder="Lớp 01A"></div>
          <div><label>Giảng viên</label><input data-section-field="instructorName" value="${esc(section.instructorName)}" placeholder="Họ tên giảng viên"></div>
          <div><label>Số phòng</label><input data-section-field="roomName" value="${esc(section.roomName)}" placeholder="A.302"></div>
          <div><label>Cơ sở</label><input data-section-field="campusName" value="${esc(section.campusName)}" placeholder="Cơ sở 1"></div>
        </div>
        <div class="manual-meetings-title">Các buổi học trong tuần</div>
        ${(section.meetings || []).map((meeting, meetingIndex) => `
          <div class="manual-meeting-row" data-meeting-index="${meetingIndex}">
            <div><label>Thứ</label><select data-meeting-field="weekday">${dayOptions.replace(`value="${Number(meeting.weekday) || 2}"`, `value="${Number(meeting.weekday) || 2}" selected`)}</select></div>
            <div><label>Tiết bắt đầu</label><input data-meeting-field="startPeriod" type="number" min="1" max="12" value="${esc(meeting.startPeriod)}"></div>
            <div><label>Tiết kết thúc</label><input data-meeting-field="endPeriod" type="number" min="1" max="12" value="${esc(meeting.endPeriod)}"></div>
            <div><label>Giờ bắt đầu</label><input data-meeting-field="startTime" type="time" value="${esc(meeting.startTime || periodToTime(meeting.startPeriod))}"></div>
            <div><label>Giờ kết thúc</label><input data-meeting-field="endTime" type="time" value="${esc(meeting.endTime || periodToTime(meeting.endPeriod, true))}"></div>
            <button type="button" data-remove-manual-meeting="${sectionIndex}:${meetingIndex}" aria-label="Xóa buổi học">Xóa</button>
          </div>`).join("")}
        <button type="button" class="manual-meeting-add" data-add-manual-meeting="${sectionIndex}">+ Thêm buổi học</button>
      </div>`).join("");
  }

  function snapshotManualDraft() {
    if (!manualDraft) return;
    manualDraft.courseCode = $("manualCourseCode").value.trim().toUpperCase();
    manualDraft.courseName = $("manualCourseName").value.trim();
    manualDraft.credits = Number($("manualCourseCredits").value);
    manualDraft.sections = [...$("manualSectionList").querySelectorAll(".manual-section-editor")].map(sectionNode => ({
      sectionCode: sectionNode.querySelector('[data-section-field="sectionCode"]').value.trim().toUpperCase(),
      className: sectionNode.querySelector('[data-section-field="className"]').value.trim(),
      instructorName: sectionNode.querySelector('[data-section-field="instructorName"]').value.trim(),
      roomName: sectionNode.querySelector('[data-section-field="roomName"]').value.trim(),
      campusName: sectionNode.querySelector('[data-section-field="campusName"]').value.trim(),
      meetings: [...sectionNode.querySelectorAll(".manual-meeting-row")].map(meetingNode => ({
        weekday: Number(meetingNode.querySelector('[data-meeting-field="weekday"]').value),
        startPeriod: Number(meetingNode.querySelector('[data-meeting-field="startPeriod"]').value),
        endPeriod: Number(meetingNode.querySelector('[data-meeting-field="endPeriod"]').value),
        startTime: meetingNode.querySelector('[data-meeting-field="startTime"]').value,
        endTime: meetingNode.querySelector('[data-meeting-field="endTime"]').value
      }))
    }));
  }

  function openManualEditor(localId = null) {
    manualEditingId = localId;
    const found = manualCourses.find(course => course.localId === localId);
    manualDraft = found ? JSON.parse(JSON.stringify(found)) : {
      localId: newLocalId("course"), courseCode: "", courseName: "", credits: 3,
      sections: [blankManualSection((manualCourses.length % 9) + 1)]
    };
    renderManualEditor();
    openOverlay("manualCourseOverlay");
    setTimeout(() => $("manualCourseCode").focus(), 40);
  }

  function closeManualEditor() {
    closeOverlay("manualCourseOverlay");
    manualDraft = null;
    manualEditingId = null;
  }

  function saveManualCourse() {
    snapshotManualDraft();
    const draft = manualDraft;
    if (!draft.courseCode || !draft.courseName || !(draft.credits > 0) || draft.credits > 30) return toast("Nhập mã, tên học phần và số tín chỉ hợp lệ.");
    if (!draft.sections.length) return toast("Thêm ít nhất một lớp học phần.");
    const duplicateCourse = manualCourses.find(course => course.courseCode.toLocaleUpperCase("vi") === draft.courseCode && course.localId !== manualEditingId);
    if (duplicateCourse) return toast("Mã học phần này đã có trong danh sách.");
    const sectionCodes = new Set();
    for (const section of draft.sections) {
      if (!section.sectionCode || !section.className || !section.instructorName || !section.roomName) return toast("Mỗi lớp cần có mã lớp, tên lớp, giảng viên và số phòng.");
      if (sectionCodes.has(section.sectionCode)) return toast(`Mã lớp ${section.sectionCode} đang bị lặp trong học phần.`);
      sectionCodes.add(section.sectionCode);
      if (!section.meetings.length) return toast(`Lớp ${section.sectionCode} cần có ít nhất một buổi học.`);
      for (const meeting of section.meetings) {
        if (meeting.weekday < 1 || meeting.weekday > 7 || meeting.startPeriod < 1 || meeting.startPeriod > 12 || meeting.endPeriod > 12 || meeting.endPeriod < meeting.startPeriod) {
          return toast(`Kiểm tra thứ và tiết học của lớp ${section.sectionCode}.`);
        }
        const startMinutes = timeToMinutes(meeting.startTime), endMinutes = timeToMinutes(meeting.endTime);
        if (Number.isFinite(startMinutes) && Number.isFinite(endMinutes) && endMinutes <= startMinutes) return toast(`Giờ kết thúc phải sau giờ bắt đầu ở lớp ${section.sectionCode}.`);
      }
      for (let left = 0; left < section.meetings.length; left++) for (let right = left + 1; right < section.meetings.length; right++) {
        if (Number(section.meetings[left].weekday) === Number(section.meetings[right].weekday) && meetingIntervalsOverlap(section.meetings[left], section.meetings[right])) {
          return toast(`Các buổi học của lớp ${section.sectionCode} đang bị trùng giờ.`);
        }
      }
    }
    const editing = Boolean(manualEditingId);
    if (editing) manualCourses = manualCourses.map(course => course.localId === manualEditingId ? draft : course);
    else manualCourses = [...manualCourses, draft];
    selectedManualCourseIds.add(draft.localId);
    persistManualCourses();
    renderStep1();
    closeManualEditor();
    toast(editing ? "Đã cập nhật học phần." : "Đã thêm học phần.");
  }

  function removeManualCourse(localId) {
    const course = manualCourses.find(item => item.localId === localId);
    if (!course || !window.confirm(`Xóa học phần ${course.courseCode} khỏi danh sách nhập tay?`)) return;
    manualCourses = manualCourses.filter(item => item.localId !== localId);
    selectedManualCourseIds.delete(localId);
    persistManualCourses();
    renderStep1();
    toast("Đã xóa học phần.");
  }

  function normalizeHeader(value) {
    return String(value || "").trim().toLocaleLowerCase("vi").normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d").replace(/[^a-z0-9]/g, "");
  }

  const columnAliases = {
    courseCode: ["mahocphan", "coursecode", "mamon", "mamh", "mahp"],
    courseName: ["tenhocphan", "coursename", "tenmon", "tenhp"],
    credits: ["tinchi", "credits", "sotinchi"],
    sectionCode: ["malophocphan", "sectioncode", "malop", "maLHP".toLowerCase()],
    className: ["tenlop", "classname", "tenlophocphan"],
    weekday: ["thu", "weekday", "ngayhoc"],
    startPeriod: ["tietbatdau", "startperiod"],
    endPeriod: ["tietketthuc", "endperiod"],
    startTime: ["giobatdau", "starttime"],
    endTime: ["gioketthuc", "endtime"],
    instructorName: ["giangvien", "instructor", "tengiangvien"],
    roomName: ["sophong", "phonghoc", "room", "roomname"],
    campusName: ["coso", "campus", "campusname"]
  };

  function parseCsvRows(text) {
    const firstLine = String(text).split(/\r?\n/, 1)[0] || "";
    const delimiter = (firstLine.match(/;/g) || []).length > (firstLine.match(/,/g) || []).length ? ";" : ",";
    const rows = [];
    let row = [], cell = "", quoted = false;
    const source = String(text).replace(/^\uFEFF/, "");
    for (let i = 0; i < source.length; i++) {
      const ch = source[i];
      if (ch === '"') {
        if (quoted && source[i + 1] === '"') { cell += '"'; i++; }
        else quoted = !quoted;
      } else if (!quoted && ch === delimiter) { row.push(cell); cell = ""; }
      else if (!quoted && (ch === "\n" || ch === "\r")) {
        if (ch === "\r" && source[i + 1] === "\n") i++;
        row.push(cell); cell = "";
        if (row.some(value => String(value).trim())) rows.push(row);
        row = [];
      } else cell += ch;
    }
    row.push(cell);
    if (row.some(value => String(value).trim())) rows.push(row);
    return rows;
  }

  function columnIndexFor(headers, field) {
    return headers.findIndex(header => columnAliases[field].includes(normalizeHeader(header)));
  }

  function extractGrid(rows) {
    const headerIndex = rows.findIndex(row => columnIndexFor(row, "courseCode") >= 0 && columnIndexFor(row, "sectionCode") >= 0);
    if (headerIndex < 0) throw new Error("Không tìm thấy hàng tiêu đề mã học phần và mã lớp học phần.");
    const headers = rows[headerIndex].map(value => String(value || "").trim());
    const indices = Object.fromEntries(Object.keys(columnAliases).map(field => [field, columnIndexFor(headers, field)]));
    const values = rows.slice(headerIndex + 1).map(row => Object.fromEntries(Object.entries(indices).map(([field, index]) => [field, index < 0 ? "" : String(row[index] ?? "").trim()])));
    return values.filter(row => Object.values(row).some(Boolean));
  }

  function xlsxColumnIndex(reference) {
    const letters = String(reference || "").replace(/[0-9]/g, "").toUpperCase();
    let result = 0;
    for (const letter of letters) result = result * 26 + letter.charCodeAt(0) - 64;
    return result - 1;
  }

  async function readXlsxRows(file) {
    const archive = new Uint8Array(await file.arrayBuffer());
    const view = new DataView(archive.buffer, archive.byteOffset, archive.byteLength);
    let eocd = -1;
    for (let pos = archive.length - 22; pos >= Math.max(0, archive.length - 65558); pos--) {
      if (view.getUint32(pos, true) === 0x06054b50) { eocd = pos; break; }
    }
    if (eocd < 0) throw new Error("Tệp Excel không có cấu trúc .xlsx hợp lệ.");
    const entries = new Map();
    const total = view.getUint16(eocd + 10, true);
    let cursor = view.getUint32(eocd + 16, true);
    const decoder = new TextDecoder("utf-8");
    for (let i = 0; i < total; i++) {
      if (view.getUint32(cursor, true) !== 0x02014b50) break;
      const method = view.getUint16(cursor + 10, true);
      const compressedSize = view.getUint32(cursor + 20, true);
      const nameLength = view.getUint16(cursor + 28, true);
      const extraLength = view.getUint16(cursor + 30, true);
      const commentLength = view.getUint16(cursor + 32, true);
      const localOffset = view.getUint32(cursor + 42, true);
      const name = decoder.decode(archive.slice(cursor + 46, cursor + 46 + nameLength));
      const localNameLength = view.getUint16(localOffset + 26, true);
      const localExtraLength = view.getUint16(localOffset + 28, true);
      const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
      const compressed = archive.slice(dataOffset, dataOffset + compressedSize);
      let data;
      if (method === 0) data = compressed;
      else if (method === 8 && typeof DecompressionStream !== "undefined") {
        const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
        data = new Uint8Array(await new Response(stream).arrayBuffer());
      } else throw new Error("Trình duyệt không hỗ trợ giải nén tệp Excel này. Hãy lưu thành CSV UTF-8 rồi nhập lại.");
      entries.set(name, decoder.decode(data));
      cursor += 46 + nameLength + extraLength + commentLength;
    }
    const parser = new DOMParser();
    const workbookXml = entries.get("xl/workbook.xml");
    const relsXml = entries.get("xl/_rels/workbook.xml.rels");
    if (!workbookXml || !relsXml) throw new Error("Không đọc được danh sách trang tính trong tệp Excel.");
    const workbookDoc = parser.parseFromString(workbookXml, "application/xml");
    const relsDoc = parser.parseFromString(relsXml, "application/xml");
    const sheet = workbookDoc.getElementsByTagName("sheet")[0];
    if (!sheet) throw new Error("Tệp Excel không có trang tính dữ liệu.");
    const relationshipId = sheet.getAttribute("r:id") || sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
    const relationship = [...relsDoc.getElementsByTagName("Relationship")].find(item => item.getAttribute("Id") === relationshipId);
    if (!relationship) throw new Error("Không tìm được trang tính đầu tiên trong tệp Excel.");
    let sheetPath = relationship.getAttribute("Target").replace(/^\//, "");
    if (!sheetPath.startsWith("xl/")) sheetPath = `xl/${sheetPath}`;
    const sheetXml = entries.get(sheetPath);
    if (!sheetXml) throw new Error("Không đọc được dữ liệu trang tính đầu tiên.");
    const sharedXml = entries.get("xl/sharedStrings.xml");
    const shared = sharedXml ? [...parser.parseFromString(sharedXml, "application/xml").getElementsByTagName("si")].map(si => [...si.getElementsByTagName("t")].map(t => t.textContent).join("")) : [];
    const doc = parser.parseFromString(sheetXml, "application/xml");
    return [...doc.getElementsByTagName("row")].map(rowNode => {
      const cells = [...rowNode.getElementsByTagName("c")];
      const row = [];
      cells.forEach(cell => {
        const index = xlsxColumnIndex(cell.getAttribute("r"));
        const type = cell.getAttribute("t");
        const valueNode = cell.getElementsByTagName("v")[0];
        let value = valueNode ? valueNode.textContent : "";
        if (type === "s") value = shared[Number(value)] || "";
        else if (type === "inlineStr") value = [...cell.getElementsByTagName("t")].map(t => t.textContent).join("");
        row[index] = value;
      });
      return row.map(value => value ?? "");
    });
  }

  function parseWeekday(value) {
    const text = normalizeHeader(value);
    if (/^(cn|chunhat|sunday|sun)$/.test(text)) return 7;
    const vietnameseDay = text.match(/thu([2-7])/);
    if (vietnameseDay) return Number(vietnameseDay[1]) - 1;
    const shortDay = text.match(/^t([2-7])$/);
    if (shortDay) return Number(shortDay[1]) - 1;
    const numericDay = text.match(/^([1-7])$/);
    return numericDay ? Number(numericDay[1]) : NaN;
  }

  function timeToMinutes(value) {
    const numeric = Number(value);
    if (String(value || "").trim() && Number.isFinite(numeric) && numeric >= 0 && numeric < 1) return Math.round(numeric * 24 * 60);
    const match = String(value || "").match(/(\d{1,2})[:h](\d{2})/i);
    return match ? Number(match[1]) * 60 + Number(match[2]) : NaN;
  }

  function normalizeTimeValue(value) {
    const text = String(value || "").trim();
    const minutes = timeToMinutes(text);
    if (!Number.isFinite(minutes)) return text;
    return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
  }

  function periodsFromTimes(startValue, endValue) {
    const start = timeToMinutes(startValue), end = timeToMinutes(endValue);
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return null;
    const boundaries = periodClock.map(timeToMinutes);
    let startPeriod = boundaries.findIndex((value, index) => index < 12 && start >= value && start < boundaries[index + 1]);
    let endPeriod = -1;
    for (let i = 0; i < 12; i++) if (end > boundaries[i] && end <= boundaries[i + 1]) endPeriod = i + 1;
    if (startPeriod < 0 || endPeriod < startPeriod + 1) return null;
    return { startPeriod: startPeriod + 1, endPeriod };
  }

  function normalizeImportedRows(rows) {
    const records = extractGrid(rows);
    const coursesByCode = new Map();
    for (const record of records) {
      const courseCode = record.courseCode.trim().toUpperCase();
      const courseName = record.courseName.trim();
      const sectionCode = record.sectionCode.trim().toUpperCase();
      const credits = Number(String(record.credits).replace(",", "."));
      const weekday = parseWeekday(record.weekday);
      const startTime = normalizeTimeValue(record.startTime), endTime = normalizeTimeValue(record.endTime);
      let startPeriod = Number(record.startPeriod), endPeriod = Number(record.endPeriod);
      if ((!startPeriod || !endPeriod) && startTime && endTime) {
        const derived = periodsFromTimes(startTime, endTime);
        if (derived) ({ startPeriod, endPeriod } = derived);
      }
      if (!courseCode || !courseName || !sectionCode || !Number.isFinite(credits) || credits <= 0) throw new Error("Có dòng thiếu mã/tên học phần, số tín chỉ hoặc mã lớp học phần.");
      if (!Number.isInteger(weekday) || weekday < 1 || weekday > 7 || !Number.isInteger(startPeriod) || !Number.isInteger(endPeriod) || startPeriod < 1 || endPeriod > 12 || endPeriod < startPeriod) {
        throw new Error(`Kiểm tra thứ hoặc tiết học của lớp ${sectionCode}.`);
      }
      if (!record.className || !record.instructorName || !record.roomName) throw new Error(`Lớp ${sectionCode} cần có tên lớp, giảng viên và số phòng.`);
      if (!coursesByCode.has(courseCode)) coursesByCode.set(courseCode, {
        localId: newLocalId("course"), courseCode, courseName, credits, sectionsByCode: new Map()
      });
      const course = coursesByCode.get(courseCode);
      if (course.courseName !== courseName || course.credits !== credits) throw new Error(`Thông tin tên hoặc tín chỉ của ${courseCode} không thống nhất giữa các dòng.`);
      if (!course.sectionsByCode.has(sectionCode)) course.sectionsByCode.set(sectionCode, {
        sectionCode, className: record.className, instructorName: record.instructorName,
        roomName: record.roomName, campusName: record.campusName, meetings: []
      });
      const section = course.sectionsByCode.get(sectionCode);
      if (section.className !== record.className || section.instructorName !== record.instructorName || section.roomName !== record.roomName) throw new Error(`Thông tin lớp ${sectionCode} không thống nhất giữa các buổi.`);
      if (!section.meetings.some(meeting => meeting.weekday === weekday && meeting.startPeriod === startPeriod && meeting.endPeriod === endPeriod)) {
        section.meetings.push({ weekday, startPeriod, endPeriod, startTime, endTime });
      }
    }
    const result = [...coursesByCode.values()].map(course => ({
      localId: course.localId, courseCode: course.courseCode, courseName: course.courseName, credits: course.credits,
      sections: [...course.sectionsByCode.values()]
    }));
    for (const course of result) for (const section of course.sections) {
      for (let left = 0; left < section.meetings.length; left++) for (let right = left + 1; right < section.meetings.length; right++) {
        if (Number(section.meetings[left].weekday) === Number(section.meetings[right].weekday) && meetingIntervalsOverlap(section.meetings[left], section.meetings[right])) {
          throw new Error(`Các buổi học của lớp ${section.sectionCode} đang bị trùng giờ.`);
        }
      }
    }
    return result;
  }

  async function importManualCourses(file) {
    try {
      const rows = file.name.toLocaleLowerCase().endsWith(".csv")
        ? parseCsvRows(await file.text())
        : await readXlsxRows(file);
      const imported = normalizeImportedRows(rows);
      if (!imported.length) throw new Error("Tệp không có dòng học phần/lớp học phần.");
      const known = new Set(manualCourses.map(course => course.courseCode.toLocaleUpperCase("vi")));
      const duplicates = imported.filter(course => known.has(course.courseCode));
      if (duplicates.length) throw new Error(`Mã học phần đã có trong danh sách: ${duplicates.map(course => course.courseCode).join(", ")}. Xóa hoặc sửa trước khi nhập lại.`);
      manualCourses = [...manualCourses, ...imported];
      imported.forEach(course => selectedManualCourseIds.add(course.localId));
      persistManualCourses();
      switchScheduleSource("manual");
      toast(`Đã nhập ${imported.length} học phần với ${imported.reduce((count, course) => count + course.sections.length, 0)} lớp.`);
    } catch (error) { toast(error.message || "Không đọc được tệp học phần."); }
  }

  function selectedCourses() {
    return courses.filter(course => selectedCourseIds.has(course.id));
  }

  function colorFor(courseId) {
    const numeric = Number(courseId) || String(courseId).split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
    return colors[Math.abs(numeric) % colors.length];
  }

  function renderStep1() {
    const query = $("schCourseSearch").value.trim().toLocaleLowerCase("vi");
    const filtered = courses.filter(course => `${course.courseCode} ${course.courseName}`.toLocaleLowerCase("vi").includes(query));
    $("schCourseList").innerHTML = filtered.length ? filtered.map(course => {
      const available = catalogSections.filter(section => section.courseId === course.id
        && section.sectionStatus === "OPEN" && section.currentEnrollment < section.maxCapacity).length;
      const selected = selectedCourseIds.has(course.id);
      return `<label class="c-row ${selected ? "picked" : ""}" data-id="${course.id}">
        <input type="checkbox" class="c-chk" ${selected ? "checked" : ""} ${available === 0 ? "disabled" : ""}>
        <span class="c-bar" style="background:var(--primary)"></span>
        <div class="c-info"><b>${esc(course.courseName)}</b><small>${esc(course.courseCode)} · ${available} lớp còn chỗ</small></div>
        <span class="c-cr">${esc(course.credits)} tín chỉ</span>
      </label>`;
    }).join("") : `<div class="empty-state">${courses.length ? "Không tìm thấy học phần phù hợp." : "Học kỳ này chưa có học phần mở."}</div>`;

    renderManualCourses();
    const chosen = scheduleSource === "manual" ? currentManualSelection() : selectedCourses();
    const credits = chosen.reduce((sum, course) => sum + Number(course.credits || 0), 0);
    $("selCourseNum").textContent = chosen.length;
    $("selCreditNum").textContent = credits;
    $("validStat").textContent = scheduleSource === "manual"
      ? `${manualCourses.length} học phần đã nhập`
      : `${courses.length} học phần từ danh mục`;
    $("donutFg").style.strokeDashoffset = 314.16 * (1 - Math.min(1, credits / 25));
    $("schSelList").innerHTML = chosen.length ? chosen.map(course => `
      <div class="sel-item"><span>${esc(course.courseName)}</span><button type="button" ${scheduleSource === "manual" ? `data-rm-manual="${esc(course.localId)}"` : `data-rm="${course.id}"`} aria-label="Bỏ chọn">&times;</button></div>
    `).join("") : `<div class="empty-state">Chưa chọn học phần nào</div>`;
  }

  function renderDoneCourses() {
    const recordsHtml = passedRecords.length ? passedRecords.map(record => `
      <div class="done-item"><span class="done-ic">✓</span><b>${esc(record.courseName || record.courseCode)}</b>
      <span class="code">${esc(record.courseCode)}</span><span class="grade">${esc(record.grade == null ? "Đạt" : record.grade)}</span></div>
    `).join("") : `<div class="empty-state">Chưa có học phần đạt nào trong bảng điểm tài khoản.</div>`;
    const eligible = prerequisiteCheck && prerequisiteCheck.eligibleCourses || [];
    const issues = prerequisiteCheck && prerequisiteCheck.ineligibleCourses || [];
    const eligibleHtml = eligible.map(course => `
      <div class="done-item"><span class="done-ic">✓</span><b>${esc(course.courseCode)} · ${esc(course.courseName)}</b><span class="grade">Đủ điều kiện</span></div>
    `).join("");
    const issuesHtml = issues.map(course => `
      <div class="tg-row" style="align-items:flex-start"><div class="tg-info"><b>${esc(course.courseCode)} · ${esc(course.courseName)}</b><small style="color:#b42318">${esc(course.reason)}</small></div></div>
    `).join("");
    $("doneCourseList").innerHTML = `<b style="display:block;margin:4px 0 8px;font-size:13px;color:#273047">Bảng điểm đã đồng bộ</b>${recordsHtml}`
      + (prerequisiteCheck ? `<div style="margin-top:14px"><b style="font-size:13px;color:#273047">Học phần đã kiểm tra điều kiện tiên quyết</b>${eligibleHtml || `<div class="empty-state">Không có học phần đủ điều kiện.</div>`}${issues.length ? `<div style="margin-top:12px"><b style="color:#b42318;font-size:13px">Học phần chưa đủ điều kiện</b>${issuesHtml}</div>` : ""}</div>` : "")
      + (scheduleSource === "manual" ? `<div class="manual-source-note" style="margin-top:14px;margin-bottom:0">Với học phần nhập tay, SmartEdu kiểm tra trùng giờ và tín chỉ trong trình duyệt. Điều kiện tiên quyết không được xác minh với hồ sơ Backend.</div>` : "");
  }

  function setStep(step) {
    document.querySelectorAll(".sch-step").forEach((node, index) => node.classList.toggle("active", index + 1 === step));
    $("schHeadActs").classList.toggle("show", step === 3);
    document.querySelectorAll("#schStepper .st-item").forEach(item => {
      const n = Number(item.dataset.step);
      item.classList.toggle("active", n === step);
      item.classList.toggle("done", n < step);
      item.querySelector(".st-num").textContent = n < step ? "✓" : n;
    });
    $("stLine1").classList.toggle("done", step > 1);
    $("stLine2").classList.toggle("done", step > 2);
    $("mainScroll").scrollTop = 0;
    if (step === 1) renderStep1();
    if (step === 2) renderDoneCourses();
    if (step === 3) {
      renderOptions();
      renderTimetable();
    }
  }

  function semesterLabel(item) {
    return [item.semesterName, item.academicYear].filter(Boolean).join(" · ") || item.semesterCode;
  }

  async function loadSemesterData(semesterId) {
    semester = semesters.find(item => Number(item.id) === Number(semesterId)) || null;
    scheduleRange = semester;
    planId = null;
    selectedCourseIds = new Set();
    prerequisiteCheck = null;
    options = [];
    activeOptionId = null;
    weeklySchedule = null;
    weekIndex = 0;
    if (!semester) {
      courses = [];
      catalogSections = [];
      renderStep1();
      return;
    }
    setStatus("Đang tải danh mục học kỳ...", "Đang lấy học phần, lớp còn chỗ và lịch học từ Backend.");
    const [courseData, sectionData] = await Promise.all([
      api(`/api/academic/semesters/${semester.id}/courses`),
      api(`/api/academic/semesters/${semester.id}/sections`)
    ]);
    courses = Array.isArray(courseData) ? courseData : [];
    catalogSections = Array.isArray(sectionData) ? sectionData : [];
    renderStep1();
    setStatus(courses.length ? "Danh mục học kỳ đã tải" : "Không có học phần mở trong học kỳ này",
      courses.length ? "Chọn học phần từ danh mục thật của học kỳ để bắt đầu." : "Hãy chọn học kỳ khác hoặc thử lại sau khi có lớp học phần.");
  }

  async function initializeSchedule() {
    if (!localStorage.getItem("smartedu_access_token")) {
      setStatus("Cần đăng nhập", "Đăng nhập SmartEdu để tải danh mục học kỳ và dữ liệu tài khoản.", true);
      return;
    }
    try {
      const data = await api("/api/academic/semesters");
      semesters = Array.isArray(data) ? data : [];
      $("schTerm").innerHTML = semesters.length ? semesters.map(item => `
        <option value="${item.id}">${esc(semesterLabel(item))}</option>`).join("") : `<option value="">Chưa có học kỳ</option>`;
      if (!semesters.length) {
        setStatus("Chưa có học kỳ", "Backend chưa trả về học kỳ để lập thời khóa biểu.", true);
        renderStep1();
        return;
      }
      await loadSemesterData(semesters[0].id);
    } catch (error) {
      setStatus("Không tải được dữ liệu", error.message, true);
      renderStep1();
    }
  }

  async function syncPlan() {
    const ids = [...selectedCourseIds];
    if (!ids.length) throw new Error("Vui lòng chọn ít nhất một học phần.");
    if (!planId) {
      const data = await api("/api/registration/plans", {
        method: "POST",
        body: JSON.stringify({ semesterId: semester.id, planName: `Kế hoạch thời khóa biểu - ${semesterLabel(semester)}`, courseIds: ids })
      });
      planId = data.planId;
    } else {
      await api(`/api/registration/plans/${planId}/courses`, { method: "PUT", body: JSON.stringify({ courseIds: ids }) });
    }
    return planId;
  }

  async function loadPlanSettings() {
    const id = await syncPlan();
    const [credit, pref, records] = await Promise.all([
      api(`/api/registration/plans/${id}/credit-limits`),
      api(`/api/registration/plans/${id}/schedule-preferences`),
      api("/api/registration/academic-records/passed")
    ]);
    passedRecords = Array.isArray(records) ? records : [];
    if (credit && credit.configured) {
      $("minCr").value = credit.minCredits;
      $("maxCr").value = credit.maxCredits;
    } else {
      $("minCr").value = "";
      $("maxCr").value = "";
    }
    const shiftButton = document.querySelector(`#shiftSeg button[data-sh="${String(pref.preferredShift || "any").toLowerCase()}"]`)
      || document.querySelector('#shiftSeg button[data-sh="any"]');
    document.querySelectorAll("#shiftSeg button").forEach(button => button.classList.toggle("active", button === shiftButton));
    $("tgNoSat").checked = Boolean(pref.avoidSaturday);
    $("tgCompact").checked = Boolean(pref.compactDays);
    $("tgNoGap").checked = Boolean(pref.minimizeGaps);
    updateCreditRange();
    renderDoneCourses();
  }

  function updateCreditRange() {
    const min = Number($("minCr").value || 0);
    const max = Number($("maxCr").value || 0);
    $("crRangeTxt").textContent = min && max ? `${min}–${max} tín chỉ` : "Nhập giới hạn tín chỉ";
    $("crFill").style.left = `${Math.max(0, Math.min(100, ((min - 12) / 13) * 100))}%`;
    $("crFill").style.right = `${Math.max(0, Math.min(100, ((25 - max) / 13) * 100))}%`;
  }

  function enumerateCombinations(courseIds) {
    const groups = courseIds.map(courseId => catalogSections.filter(section => Number(section.courseId) === Number(courseId)
      && section.sectionStatus === "OPEN" && Number(section.currentEnrollment) < Number(section.maxCapacity)));
    const unavailableIndex = groups.findIndex(group => !group.length);
    if (unavailableIndex >= 0) {
      const course = courses.find(item => Number(item.id) === Number(courseIds[unavailableIndex]));
      throw new Error(`Học phần ${course ? course.courseCode : courseIds[unavailableIndex]} hiện không có lớp còn chỗ.`);
    }
    const result = [];
    function build(index, current) {
      if (result.length >= 10000) return;
      if (index === groups.length) {
        result.push([...current]);
        return;
      }
      for (const section of groups[index]) {
        current.push(section.id);
        build(index + 1, current);
        current.pop();
        if (result.length >= 10000) break;
      }
    }
    build(0, []);
    return result;
  }

  function manualCombinationConflicts(sections) {
    const conflicts = [];
    for (let leftIndex = 0; leftIndex < sections.length; leftIndex++) {
      for (let rightIndex = leftIndex + 1; rightIndex < sections.length; rightIndex++) {
        const left = sections[leftIndex], right = sections[rightIndex];
        for (const a of left.meetings || []) for (const b of right.meetings || []) {
          if (Number(a.weekday) === Number(b.weekday) && meetingIntervalsOverlap(a, b)) {
            const leftTime = `${a.startTime || periodToTime(a.startPeriod)}–${a.endTime || periodToTime(a.endPeriod, true)}`;
            const rightTime = `${b.startTime || periodToTime(b.startPeriod)}–${b.endTime || periodToTime(b.endPeriod, true)}`;
            conflicts.push(`${left.courseCode} · ${left.sectionCode} (${leftTime}) và ${right.courseCode} · ${right.sectionCode} (${rightTime}) trùng ${weekDayLabel(a.weekday)}.`);
          }
        }
      }
    }
    return conflicts;
  }

  function meetingIntervalsOverlap(a, b) {
    const aStart = timeToMinutes(a.startTime), aEnd = timeToMinutes(a.endTime), bStart = timeToMinutes(b.startTime), bEnd = timeToMinutes(b.endTime);
    if (Number.isFinite(aStart) && Number.isFinite(aEnd) && Number.isFinite(bStart) && Number.isFinite(bEnd)) return aStart < bEnd && bStart < aEnd;
    return Number(a.startPeriod) <= Number(b.endPeriod) && Number(b.startPeriod) <= Number(a.endPeriod);
  }

  function manualScheduleScore(sections) {
    const meetings = sections.flatMap(section => section.meetings || []);
    const activeDays = new Set(meetings.map(meeting => Number(meeting.weekday)));
    const shift = document.querySelector("#shiftSeg button.active")?.dataset.sh || "any";
    const preferMorning = meetings.filter(meeting => Number(meeting.startPeriod) <= 6).length;
    const preferAfternoon = meetings.filter(meeting => Number(meeting.startPeriod) >= 7).length;
    const shiftContribution = shift === "morning" ? Math.round(40 * preferMorning / Math.max(meetings.length, 1))
      : shift === "afternoon" ? Math.round(40 * preferAfternoon / Math.max(meetings.length, 1)) : 20;
    const avoidSaturday = $("tgNoSat").checked;
    const saturdayContribution = avoidSaturday ? (activeDays.has(6) ? 0 : 25) : 12;
    const compactDays = $("tgCompact").checked;
    const compactContribution = compactDays ? Math.max(0, 20 - Math.max(0, activeDays.size - 2) * 4) : 10;
    let gaps = 0;
    for (let day = 1; day <= 7; day++) {
      const dayMeetings = meetings.filter(meeting => Number(meeting.weekday) === day).sort((a, b) => a.startPeriod - b.startPeriod);
      for (let i = 1; i < dayMeetings.length; i++) gaps += Math.max(0, dayMeetings[i].startPeriod - dayMeetings[i - 1].endPeriod - 1);
    }
    const gapContribution = $("tgNoGap").checked ? Math.max(0, 15 - gaps * 3) : 8;
    const score = shiftContribution + saturdayContribution + compactContribution + gapContribution;
    const explanation = [
      `Ưu tiên ca học: ${shift === "any" ? "không đặt ưu tiên" : shift === "morning" ? `${preferMorning}/${meetings.length} buổi bắt đầu buổi sáng` : `${preferAfternoon}/${meetings.length} buổi bắt đầu buổi chiều`}.`,
      `${activeDays.size} ngày học trong tuần${avoidSaturday ? (activeDays.has(6) ? ", có học Thứ 7" : ", không học Thứ 7") : ""}.`,
      `${gaps} tiết trống giữa các buổi.`
    ].join(" ");
    return { score, gaps, activeDays: activeDays.size, evaluations: [
      { criterionCode: "SHIFT", criterionName: "Ca học ưu tiên", scoreValue: shiftContribution, weightValue: "1", explanation: `Đạt ${shiftContribution}/40 điểm. ${shift === "any" ? "Không áp dụng ưu tiên ca." : `${shift === "morning" ? preferMorning : preferAfternoon} buổi phù hợp.`}` },
      { criterionCode: "SATURDAY", criterionName: "Tránh học Thứ 7", scoreValue: saturdayContribution, weightValue: "1", explanation: `${saturdayContribution}/25 điểm.` },
      { criterionCode: "COMPACT", criterionName: "Gom lịch học", scoreValue: compactContribution, weightValue: "1", explanation: `${activeDays.size} ngày có lịch, ${compactContribution}/20 điểm.` },
      { criterionCode: "GAPS", criterionName: "Khoảng trống giữa ca", scoreValue: gapContribution, weightValue: "1", explanation: `${gaps} tiết trống, ${gapContribution}/15 điểm.` }
    ], explanation };
  }

  function createManualOption(sections, rank = null) {
    const scoring = manualScheduleScore(sections);
    const optionId = -(Date.now() * 1000 + ++manualOptionSequence);
    const optionSections = sections.map((section, sectionIndex) => ({
      sectionId: section.localSectionId || `${section.courseId}-${section.sectionCode}`,
      courseId: section.courseId, courseCode: section.courseCode, courseName: section.courseName,
      credits: Number(section.credits), sectionCode: section.sectionCode, className: section.className,
      instructorName: section.instructorName, roomName: section.roomName, campusName: section.campusName,
      meetings: section.meetings.map((meeting, meetingIndex) => ({
        ...meeting, meetingId: `${optionId}-${sectionIndex}-${meetingIndex}`, weekday: Number(meeting.weekday),
        startTime: meeting.startTime || periodToTime(meeting.startPeriod), endTime: meeting.endTime || periodToTime(meeting.endPeriod, true),
        courseId: section.courseId, courseCode: section.courseCode, courseName: section.courseName,
        sectionId: section.localSectionId || `${section.courseId}-${section.sectionCode}`, sectionCode: section.sectionCode, className: section.className,
        instructorName: section.instructorName, roomName: section.roomName, campusName: section.campusName
      }))
    }));
    return {
      optionId, optionName: rank ? `Phương án #${rank}` : "Phương án nhập tay đã lưu", optionRank: rank,
      totalCredits: sections.reduce((sum, section) => sum + Number(section.credits || 0), 0),
      totalScore: scoring.score, feasible: true, manual: true, sections: optionSections,
      evaluations: scoring.evaluations, warnings: [], explanation: scoring.explanation
    };
  }

  function manualWeeklySchedule(option) {
    return {
      optionId: option.optionId, optionName: option.optionName, optionRank: option.optionRank,
      totalCredits: option.totalCredits, totalScore: option.totalScore,
      meetings: (option.sections || []).flatMap(section => section.meetings || [])
    };
  }

  function enumerateManualOptions() {
    const chosenCourses = currentManualSelection();
    if (!chosenCourses.length) throw new Error("Chọn ít nhất một học phần nhập tay.");
    const groups = chosenCourses.map(course => (course.sections || []).map(section => ({
      ...section, courseId: course.localId, courseCode: course.courseCode, courseName: course.courseName, credits: course.credits,
      localSectionId: `${course.localId}:${section.sectionCode}`
    })));
    if (groups.some(group => !group.length)) throw new Error("Mỗi học phần cần ít nhất một lớp học phần.");
    const minCredits = Number($("minCr").value), maxCredits = Number($("maxCr").value);
    if (!Number.isFinite(minCredits) || !Number.isFinite(maxCredits) || minCredits < 0 || maxCredits <= 0 || minCredits > maxCredits) throw new Error("Nhập khoảng tín chỉ hợp lệ.");
    const all = [];
    function build(index, chosen) {
      if (all.length >= 10000) return;
      if (index === groups.length) { all.push([...chosen]); return; }
      for (const section of groups[index]) { chosen.push(section); build(index + 1, chosen); chosen.pop(); }
    }
    build(0, []);
    let conflictRejected = 0, creditRejected = 0;
    const valid = [];
    const conflictExamples = [];
    for (const combo of all) {
      const overlaps = manualCombinationConflicts(combo);
      const credits = combo.reduce((sum, section) => sum + Number(section.credits || 0), 0);
      const hasCreditIssue = credits < minCredits || credits > maxCredits;
      if (overlaps.length) { conflictRejected++; if (conflictExamples.length < 3) conflictExamples.push(...overlaps.slice(0, 1)); }
      if (hasCreditIssue) creditRejected++;
      if (overlaps.length || hasCreditIssue) continue;
      valid.push({ sections: combo, credits });
    }
    $("scheduleCheckDetails").innerHTML = `<b>PB26 · Trùng lịch:</b> ${all.length - conflictRejected}/${all.length} tổ hợp không trùng.`
      + (conflictExamples.length ? `<ul>${conflictExamples.map(line => `<li>${esc(line)}</li>`).join("")}</ul>` : " Không phát hiện xung đột.")
      + `<b>PB28 · Giới hạn tín chỉ:</b> ${all.length - creditRejected}/${all.length} tổ hợp đạt ${minCredits}–${maxCredits} tín chỉ.`
      + `<b>PB27 · Điều kiện tiên quyết:</b> Không kiểm tra với dữ liệu nhập tay; lớp học phần không nằm trong danh mục Backend.`;
    if (!valid.length) throw new Error("Không tìm thấy tổ hợp hợp lệ. Kiểm tra lớp bị trùng và khoảng tín chỉ.");
    valid.sort((a, b) => manualScheduleScore(b.sections).score - manualScheduleScore(a.sections).score);
    return { totalCandidates: all.length, conflictRejected, creditRejected, valid: valid.slice(0, 10) };
  }

  async function createManualSchedule(button) {
    if (scheduleBusy) return;
    scheduleBusy = true;
    setBusy(button, true, "Đang kiểm tra lớp và xếp lịch...");
    try {
      const result = enumerateManualOptions();
      options = result.valid.map((combo, index) => createManualOption(combo.sections, index + 1));
      activeOptionId = options.length ? options[0].optionId : null;
      if (!options.length) throw new Error("Không tìm thấy thời khóa biểu hợp lệ.");
      weeklySchedule = manualWeeklySchedule(options[0]);
      scheduleRange = null;
      activeScheduleSemesterId = null;
      $("optHeadSummary").textContent = `${result.totalCandidates} tổ hợp · ${result.conflictRejected} có xung đột · ${result.creditRejected} sai giới hạn tín chỉ · ${options.length} hợp lệ`;
      setStatus("Đã tạo phương án từ dữ liệu nhập tay", `${result.totalCandidates} tổ hợp đã xét · ${result.conflictRejected} tổ hợp trùng giờ · ${result.creditRejected} tổ hợp không đạt tín chỉ.`, false);
      weekIndex = 0;
      setStep(3);
      updateWeekLabel();
      toast("Đã kiểm tra trùng giờ, giới hạn tín chỉ và xếp hạng phương án nhập tay.");
    } catch (error) {
      setStatus("Không thể tạo thời khóa biểu", error.message, true);
      toast(error.message);
    } finally {
      scheduleBusy = false;
      setBusy(button, false);
    }
  }

  async function savePreferences() {
    const minCredits = Number($("minCr").value);
    const maxCredits = Number($("maxCr").value);
    if (!Number.isFinite(minCredits) || !Number.isFinite(maxCredits) || minCredits < 0 || maxCredits <= 0 || minCredits > maxCredits) {
      throw new Error("Nhập khoảng tín chỉ hợp lệ; số tối thiểu không được lớn hơn số tối đa.");
    }
    await api(`/api/registration/plans/${planId}/credit-limits`, {
      method: "PUT", body: JSON.stringify({ minCredits, maxCredits })
    });
    const shift = document.querySelector("#shiftSeg button.active")?.dataset.sh || "any";
    await api(`/api/registration/plans/${planId}/schedule-preferences`, {
      method: "PUT",
      body: JSON.stringify({
        preferredShift: shift === "any" ? "ANY" : shift.toUpperCase(),
        avoidSaturday: $("tgNoSat").checked,
        compactDays: $("tgCompact").checked,
        minimizeGaps: $("tgNoGap").checked,
        preferredFreeDays: [],
        maxGapPeriods: null
      })
    });
  }

  async function createSchedule(button) {
    if (scheduleSource === "manual") return createManualSchedule(button);
    if (scheduleBusy) return;
    scheduleBusy = true;
    setBusy(button, true, "Đang kiểm tra và xếp lịch...");
    try {
      await syncPlan();
      await savePreferences();
      const requestedIds = [...selectedCourseIds];
      prerequisiteCheck = await api("/api/registration/checks/prerequisites", {
        method: "POST", body: JSON.stringify({ planId, courseIds: requestedIds })
      });
      renderDoneCourses();
      const ineligibleIds = new Set((prerequisiteCheck.ineligibleCourses || []).map(course => Number(course.courseId)));
      if (ineligibleIds.size) {
        selectedCourseIds = new Set([...selectedCourseIds].filter(id => !ineligibleIds.has(Number(id))));
        await api(`/api/registration/plans/${planId}/courses`, {
          method: "PUT", body: JSON.stringify({ courseIds: [...selectedCourseIds] })
        });
        renderStep1();
        renderDoneCourses();
        toast(`Đã bỏ ${ineligibleIds.size} học phần chưa đủ điều kiện; lý do được hiển thị ở bước 2.`);
      }
      if (!selectedCourseIds.size) throw new Error("Không còn học phần đủ điều kiện để tạo thời khóa biểu.");

      const combinations = enumerateCombinations([...selectedCourseIds]);
      if (!combinations.length) throw new Error("Không tìm thấy tổ hợp lớp học phần để kiểm tra.");
      setStatus("Đang kiểm tra điều kiện bắt buộc...", `Đang kiểm tra ${combinations.length} tổ hợp lớp học phần từ Backend.`);
      const [conflicts, credits] = await Promise.all([
        api("/api/registration/checks/schedule-conflicts", {
          method: "POST", body: JSON.stringify({ planId, combinations })
        }),
        api("/api/registration/checks/credit-limits", {
          method: "POST", body: JSON.stringify({ planId, combinations })
        })
      ]);
      setStatus("Đang xếp hạng phương án...",
        `Trùng lịch: ${conflicts.validCount}/${conflicts.totalChecked} hợp lệ · Tín chỉ: ${credits.validCount}/${credits.totalChecked} đạt giới hạn.`);
      const conflictExamples = (conflicts.rejectedCombinations || []).flatMap(combo => (combo.conflicts || []).map(item =>
        `${item.sectionACode || "Lớp A"} và ${item.sectionBCode || "Lớp B"} trùng thứ ${item.weekday}, ${item.sectionAPeriods || ""} với ${item.sectionBPeriods || ""}`));
      const creditExamples = (credits.rejectedCombinations || []).slice(0, 3).map(item => item.reason || `Tổ hợp ${item.combinationIndex}: ${item.totalCredits} tín chỉ không nằm trong giới hạn.`);
      $("scheduleCheckDetails").innerHTML = `<b>PB26 · Trùng lịch:</b> ${conflicts.validCount}/${conflicts.totalChecked} tổ hợp hợp lệ.`
        + (conflictExamples.length ? `<ul>${conflictExamples.slice(0, 3).map(line => `<li>${esc(line)}</li>`).join("")}</ul>` : " Không phát hiện xung đột.")
        + `<b>PB28 · Giới hạn tín chỉ:</b> ${credits.validCount}/${credits.totalChecked} tổ hợp đạt.`
        + (creditExamples.length ? `<ul>${creditExamples.map(line => `<li>${esc(line)}</li>`).join("")}</ul>` : " Tất cả tổ hợp đạt giới hạn.");
      const generated = await api("/api/schedules/generate", {
        method: "POST", body: JSON.stringify({ registrationPlanId: planId, maxOptions: 20 })
      });
      options = Array.isArray(generated.options) ? generated.options : [];
      activeOptionId = options.length ? options[0].optionId : null;
      if (!options.length) {
        setStatus("Không có phương án hợp lệ", `Đã xét ${generated.totalCandidates} tổ hợp; không có tổ hợp nào cùng thỏa thời khóa biểu, điều kiện và giới hạn tín chỉ.`, true);
        throw new Error("Không tìm thấy thời khóa biểu đáp ứng các điều kiện bắt buộc.");
      }
      setStatus("Đã tạo phương án thời khóa biểu",
        `${generated.totalCandidates} tổ hợp đã xét · ${generated.feasibleCandidates} tổ hợp hợp lệ · ${options.length} phương án được xếp hạng.`);
      $("optHeadSummary").textContent = `${generated.totalCandidates} tổ hợp · ${generated.totalCandidates - generated.feasibleCandidates} bị loại · ${generated.feasibleCandidates} hợp lệ`;
      weekIndex = 0;
      setStep(3);
      await loadWeekly(activeOptionId);
      toast("Đã tạo và xếp hạng phương án từ dữ liệu Backend.");
    } catch (error) {
      if (!$("scheduleStatusTitle").textContent.startsWith("Không có phương án")) setStatus("Không thể tạo thời khóa biểu", error.message, true);
      toast(error.message);
    } finally {
      scheduleBusy = false;
      setBusy(button, false);
    }
  }

  function activeOption() {
    return options.find(option => String(option.optionId) === String(activeOptionId)) || null;
  }

  async function loadWeekly(optionId) {
    if (!optionId) return;
    const option = options.find(item => String(item.optionId) === String(optionId));
    if (option && option.manual) {
      weeklySchedule = manualWeeklySchedule(option);
      scheduleRange = null;
      activeScheduleSemesterId = null;
      renderOptions();
      renderTimetable();
      updateWeekLabel();
      return;
    }
    try {
      weeklySchedule = await api(`/api/schedules/options/${optionId}/weekly`);
      activeScheduleSemesterId = weeklySchedule.semesterId || semester?.id || null;
      scheduleRange = {
        startDate: weeklySchedule.semesterStartDate || semester?.startDate,
        endDate: weeklySchedule.semesterEndDate || semester?.endDate
      };
      renderOptions();
      renderTimetable();
      updateWeekLabel();
    } catch (error) {
      weeklySchedule = null;
      renderTimetable();
      toast(error.message);
    }
  }

  function renderOptions() {
    const summary = $("optHeadSummary");
    if (summary && !summary.textContent) summary.textContent = `${options.length} phương án hợp lệ`;
    $("optList").innerHTML = options.length ? options.map(option => {
      const selected = Number(option.optionId) === Number(activeOptionId);
      const daysCount = new Set((option.sections || []).flatMap(section => (section.meetings || []).map(meeting => meeting.weekday))).size;
      const warnings = (option.warnings || []).length;
      const checks = option.manual
        ? `<span>Không trùng lịch</span><span>Đạt giới hạn tín chỉ</span><span>Nhập tay</span>`
        : `<span>Đã kiểm tra xung đột</span><span>Đủ điều kiện</span>`;
      return `<div class="opt-card ${selected ? "active" : ""}" data-oid="${option.optionId}">
        <div class="opt-top"><span class="opt-badge">${option.optionRank ? `#${option.optionRank}` : "✎"}</span>
        <div class="opt-title"><b>${esc(option.optionName)}</b><small>${esc(option.totalCredits)} tín chỉ · ${daysCount} ngày học</small></div>
        <div class="opt-score"><b>${esc(option.totalScore)}</b><small>điểm</small></div></div>
        <div class="opt-tags">${checks}${warnings ? `<span>${warnings} cảnh báo</span>` : ""}</div>
      </div>`;
    }).join("") : `<div class="empty-state">Chưa có phương án thời khóa biểu. Chọn học phần, lưu tiêu chí và tạo lịch.</div>`;
    const option = activeOption();
    if (option) {
      const sectionCount = (option.sections || []).length;
      $("ttOptTitle").textContent = option.optionRank ? `Phương án #${option.optionRank}` : option.optionName;
      $("ttBestPill").style.display = option.optionRank === 1 ? "inline-block" : "none";
      const freeDays = Math.max(0, 7 - new Set((option.sections || []).flatMap(section => (section.meetings || []).map(meeting => meeting.weekday))).size);
      $("ttSub").textContent = `${option.totalCredits} tín chỉ · ${sectionCount} học phần · ${freeDays} ngày trống`;
      $("aiWhyDesc").textContent = (option.evaluations || []).filter(item => Number(item.weightValue) !== 0)
        .map(item => `${item.criterionName}: ${item.explanation}`).join(" ") || "Điểm được tính theo các tiêu chí đã lưu.";
    }
  }

  function periodTime(period, useEnd = false) {
    const meeting = (weeklySchedule?.meetings || []).find(item => useEnd
      ? Number(item.endPeriod) === Number(period) && item.endTime
      : Number(item.startPeriod) === Number(period) && item.startTime);
    return meeting ? String(useEnd ? meeting.endTime : meeting.startTime).slice(0, 5) : "";
  }

  function renderTimetable() {
    const meetings = weeklySchedule && Array.isArray(weeklySchedule.meetings) ? weeklySchedule.meetings : [];
    if (!meetings.length) {
      $("ttBody").innerHTML = `<tr><td colspan="8"><div class="empty-state">Chưa có dữ liệu lịch học từ Backend.</div></td></tr>`;
      return;
    }
    const byDay = new Map();
    meetings.forEach(meeting => {
      const day = Number(meeting.weekday);
      if (!byDay.has(day)) byDay.set(day, []);
      byDay.get(day).push(meeting);
    });
    const occupied = new Set();
    const rows = [];
    for (let period = 1; period <= 12; period++) {
      const displayTime = periodTime(period);
      let row = `<tr><td><div class="slot-t">${displayTime || `Tiết ${period}`}</div><div class="slot-p">Tiết ${period}</div></td>`;
      for (let day = 1; day <= 7; day++) {
        const key = `${day}-${period}`;
        if (occupied.has(key)) continue;
        const meeting = (byDay.get(day) || []).find(item => Number(item.startPeriod) === period);
        if (!meeting) { row += "<td></td>"; continue; }
        const span = Math.max(1, Math.min(12 - period + 1, Number(meeting.endPeriod) - period + 1));
        for (let covered = period + 1; covered < period + span; covered++) occupied.add(`${day}-${covered}`);
        const course = esc(meeting.courseName);
        const courseCode = esc(meeting.courseCode);
        const timeText = [meeting.startTime, meeting.endTime].filter(Boolean).map(time => String(time).slice(0, 5)).join("–");
        row += `<td rowspan="${span}"><div class="ev-card ${colorFor(meeting.courseId)}">
          <div class="ev-top"><b title="${course}">${course}</b></div>
          <small>${courseCode} · ${esc(meeting.sectionCode)}${meeting.className ? ` · ${esc(meeting.className)}` : ""}</small>
          <small>${esc(meeting.roomName || "Chưa xếp phòng")}${meeting.campusName ? ` · ${esc(meeting.campusName)}` : ""}</small>
          <small>${timeText || `Tiết ${meeting.startPeriod}–${meeting.endPeriod}`} · ${esc(meeting.instructorName || "Chưa phân công")}</small>
          ${activeOption()?.manual ? "" : `<button type="button" data-edit-section="${meeting.sectionId}" data-course="${meeting.courseId}">Đổi lớp</button>`}
        </div></td>`;
      }
      row += "</tr>";
      rows.push(row);
    }
    $("ttBody").innerHTML = rows.join("");
    document.querySelectorAll(".tt th").forEach((header, index) => { if (index) header.textContent = days[index - 1]; });
  }

  function updateWeekLabel() {
    if (!scheduleRange || !scheduleRange.startDate || !scheduleRange.endDate) {
      $("weekLabel").textContent = `Tuần ${weekIndex + 1} · Lịch lặp hàng tuần`;
      return;
    }
    const start = new Date(`${scheduleRange.startDate}T00:00:00`);
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    const semesterEnd = new Date(`${scheduleRange.endDate}T00:00:00`);
    if (end > semesterEnd) end.setTime(semesterEnd.getTime());
    const fmt = date => `${String(date.getDate()).padStart(2, "0")}/${String(date.getMonth() + 1).padStart(2, "0")}`;
    $("weekLabel").textContent = `Tuần ${weekIndex + 1} · ${fmt(start)}–${fmt(end)}`;
    $("prevWeek").disabled = weekIndex <= 0;
    $("nextWeek").disabled = end >= semesterEnd;
  }

  function openOverlay(id) {
    $(id).classList.add("open");
    document.body.classList.add("modal-open");
  }
  function closeOverlay(id) {
    $(id).classList.remove("open");
    if (!document.querySelector(".overlay.open")) document.body.classList.remove("modal-open");
  }

  async function saveSchedule() {
    const option = activeOption();
    const displayName = $("planAliasInput").value.trim();
    if (!option || !displayName) return toast("Chọn phương án và nhập tên gợi nhớ.");
    try {
      if (option.manual) {
        const saved = JSON.parse(localStorage.getItem("smartedu_manual_schedule_saves") || "[]");
        saved.unshift({ id: `manual-${Date.now()}`, manual: true, displayName, option, savedAt: new Date().toISOString() });
        localStorage.setItem("smartedu_manual_schedule_saves", JSON.stringify(saved));
        closeOverlay("saveTtOverlay");
        toast("Đã lưu phương án nhập tay trên thiết bị này.");
        return;
      }
      await api("/api/schedules/saved", {
        method: "POST", body: JSON.stringify({ optionId: option.optionId, displayName })
      });
      closeOverlay("saveTtOverlay");
      toast("Đã lưu phương án vào tài khoản của bạn.");
    } catch (error) { toast(error.message); }
  }

  async function showSavedSchedules() {
    $("savedSchedulesList").innerHTML = `<div class="empty-state">Đang tải...</div>`;
    openOverlay("savedSchedulesOverlay");
    try {
      let remote = [];
      if (localStorage.getItem("smartedu_access_token")) remote = await api("/api/schedules/saved");
      const local = JSON.parse(localStorage.getItem("smartedu_manual_schedule_saves") || "[]");
      savedSchedules = [...(Array.isArray(local) ? local : []), ...(Array.isArray(remote) ? remote : [])];
      renderSavedSchedules();
    } catch (error) { $("savedSchedulesList").innerHTML = `<div class="empty-state">${esc(error.message)}</div>`; }
  }

  function renderSavedSchedules() {
    $("savedSchedulesList").innerHTML = savedSchedules.length ? savedSchedules.map(saved => `
      <div class="saved-row" data-saved-id="${esc(saved.id)}" data-saved-source="${saved.manual ? "manual" : "server"}" data-option-id="${esc(saved.optionId || saved.option?.optionId)}">
        <div><b>${esc(saved.displayName)}</b><small>${esc(saved.manual ? saved.option?.optionName : saved.optionName)} · ${saved.savedAt ? new Date(saved.savedAt).toLocaleString("vi-VN") : ""}${saved.manual ? " · Lưu trên thiết bị" : ""}</small></div>
        <div class="saved-actions"><button type="button" data-action="view">Xem lịch</button><button type="button" data-action="rename">Đổi tên</button><button type="button" data-action="delete">Xóa</button></div>
      </div>`).join("") : `<div class="empty-state">Bạn chưa lưu phương án nào.</div>`;
  }

  async function handleSavedAction(event) {
    const button = event.target.closest("button[data-action]");
    const row = event.target.closest(".saved-row");
    if (!button || !row) return;
    const saved = savedSchedules.find(item => String(item.id) === String(row.dataset.savedId));
    if (!saved) return;
    try {
      if (saved.manual) {
        if (button.dataset.action === "view") {
          const option = saved.option;
          if (!options.some(item => String(item.optionId) === String(option.optionId))) options.push(option);
          activeOptionId = option.optionId;
          weeklySchedule = manualWeeklySchedule(option);
          closeOverlay("savedSchedulesOverlay");
          setStep(3);
          updateWeekLabel();
        } else if (button.dataset.action === "rename") {
          const name = window.prompt("Tên gợi nhớ mới:", saved.displayName);
          if (!name || !name.trim()) return;
          const local = JSON.parse(localStorage.getItem("smartedu_manual_schedule_saves") || "[]").map(item => String(item.id) === String(saved.id) ? { ...item, displayName: name.trim() } : item);
          localStorage.setItem("smartedu_manual_schedule_saves", JSON.stringify(local));
          savedSchedules = savedSchedules.map(item => String(item.id) === String(saved.id) ? { ...item, displayName: name.trim() } : item);
          renderSavedSchedules();
        } else if (button.dataset.action === "delete") {
          if (!window.confirm(`Xóa phương án “${saved.displayName}”?`)) return;
          const local = JSON.parse(localStorage.getItem("smartedu_manual_schedule_saves") || "[]").filter(item => String(item.id) !== String(saved.id));
          localStorage.setItem("smartedu_manual_schedule_saves", JSON.stringify(local));
          savedSchedules = savedSchedules.filter(item => String(item.id) !== String(saved.id));
          renderSavedSchedules();
        }
        return;
      }
      if (button.dataset.action === "view") {
        const optionId = Number(saved.optionId);
        let option = options.find(item => Number(item.optionId) === optionId);
        if (!option) {
          const [weekly, evaluation] = await Promise.all([
            api(`/api/schedules/options/${optionId}/weekly`),
            api(`/api/schedules/options/${optionId}/evaluation`)
          ]);
          const sectionsById = new Map();
          (weekly.meetings || []).forEach(meeting => {
            if (!sectionsById.has(meeting.sectionId)) sectionsById.set(meeting.sectionId, {
              sectionId: meeting.sectionId, courseId: meeting.courseId, courseCode: meeting.courseCode,
              courseName: meeting.courseName, credits: meeting.credits, sectionCode: meeting.sectionCode,
              instructorName: meeting.instructorName, meetings: []
            });
            sectionsById.get(meeting.sectionId).meetings.push(meeting);
          });
          option = {
            optionId, optionName: weekly.optionName, optionRank: weekly.optionRank,
            totalCredits: weekly.totalCredits, totalScore: weekly.totalScore, feasible: true,
            sections: [...sectionsById.values()], evaluations: evaluation.evaluations || [], warnings: evaluation.warnings || []
          };
          options.push(option);
          weeklySchedule = weekly;
        }
        if (!option) throw new Error("Không tải được chi tiết phương án đã lưu.");
        activeOptionId = optionId;
        weekIndex = 0;
        closeOverlay("savedSchedulesOverlay");
        setStep(3);
        await loadWeekly(optionId);
      } else if (button.dataset.action === "rename") {
        const name = window.prompt("Tên gợi nhớ mới:", saved.displayName);
        if (!name || !name.trim()) return;
        const updated = await api(`/api/schedules/saved/${saved.id}`, { method: "PUT", body: JSON.stringify({ displayName: name.trim() }) });
        savedSchedules = savedSchedules.map(item => String(item.id) === String(saved.id) ? updated : item);
        renderSavedSchedules();
        toast("Đã cập nhật tên phương án.");
      } else if (button.dataset.action === "delete") {
        if (!window.confirm(`Xóa phương án “${saved.displayName}”?`)) return;
        await api(`/api/schedules/saved/${saved.id}`, { method: "DELETE" });
        savedSchedules = savedSchedules.filter(item => String(item.id) !== String(saved.id));
        renderSavedSchedules();
        toast("Đã xóa phương án đã lưu.");
      }
    } catch (error) { toast(error.message); }
  }

  function openCompare() {
    selectedForCompare = new Set(activeOptionId ? [Number(activeOptionId)] : []);
    renderCompare();
    openOverlay("compareOverlay");
  }

  function renderCompare(result = null) {
    const resultMap = result ? new Map(result.map(option => [Number(option.optionId), option])) : null;
    $("cmpGrid").innerHTML = options.map(option => {
      const checked = selectedForCompare.has(Number(option.optionId));
      const detail = resultMap && resultMap.get(Number(option.optionId)) || option;
      const daysCount = new Set((detail.sections || []).flatMap(section => (section.meetings || []).map(meeting => meeting.weekday))).size;
      const gaps = (detail.evaluations || []).find(item => item.criterionCode === "GAPS");
      const sectionSummary = (detail.sections || []).map(section => {
        const meetingSummary = (section.meetings || []).map(meeting => `T${meeting.weekday} ${meeting.startPeriod}–${meeting.endPeriod}`).join(", ");
        return `${section.courseCode}: ${section.sectionCode}${section.className ? ` · ${section.className}` : ""}${meetingSummary ? ` (${meetingSummary})` : ""}`;
      }).join(" · ");
      return `<div class="cmp-col ${option.optionRank === 1 ? "rec" : ""}">
        ${option.optionRank === 1 ? '<span class="cmp-rec-tag">ĐIỂM CAO NHẤT</span>' : ""}
        <h4>${esc(option.optionName)}</h4><div class="cmp-score"><b>${esc(option.totalScore)}</b><span>điểm</span></div>
        <label class="cmp-check"><input type="checkbox" data-compare="${option.optionId}" ${checked ? "checked" : ""}> So sánh phương án này</label>
        <div class="cmp-rows"><div>${esc(option.totalCredits)} tín chỉ</div><div>${daysCount} ngày học / tuần</div>
          <div>${(detail.sections || []).length} lớp học phần</div>
          <div style="max-height:130px;overflow:auto;line-height:1.5">${esc(sectionSummary || "Chưa có thông tin lớp")}</div>
          <div>${gaps ? `Điểm khoảng trống: ${esc(gaps.scoreValue)} × ${esc(gaps.weightValue)}` : "Chưa có giải thích khoảng trống"}</div>
        </div><button type="button" class="btn-pick-opt" data-pick="${option.optionId}">Xem phương án</button>
      </div>`;
    }).join("");
    $("cmpGrid").insertAdjacentHTML("beforeend", `<div style="grid-column:1/-1;display:flex;justify-content:flex-end;gap:10px"><button type="button" class="btn-submit" id="runCompare">So sánh đã chọn</button></div>`);
  }

  async function compareSelected() {
    const ids = [...selectedForCompare];
    if (ids.length < 2 || ids.length > 3) return toast("Chọn từ 2 đến 3 phương án để so sánh.");
    const selectedOptions = options.filter(option => ids.includes(Number(option.optionId)));
    if (selectedOptions.some(option => option.manual) && selectedOptions.some(option => !option.manual)) return toast("Hãy so sánh các phương án cùng một nguồn dữ liệu.");
    if (selectedOptions.every(option => option.manual)) {
      renderCompare(selectedOptions);
      return toast("Đã so sánh các phương án nhập tay.");
    }
    try {
      const result = await api("/api/schedules/compare", { method: "POST", body: JSON.stringify({ optionIds: ids }) });
      renderCompare(result.options || []);
      toast("Đã tải dữ liệu so sánh từ Backend.");
    } catch (error) { toast(error.message); }
  }

  function openEvaluation() {
    const option = activeOption();
    if (!option) return;
    const items = option.evaluations || [];
    const rows = items.map(item => `
      <div class="done-item"><b>${esc(item.criterionName)}</b><span class="code">${esc(item.scoreValue)} × ${esc(item.weightValue)} điểm</span><small>${esc(item.explanation)}</small></div>
    `).join("");
    const warningRows = (option.warnings || []).map(warning => `<div class="tg-row"><div class="tg-info"><b>${esc(warning.warningType)} · ${esc(warning.severity)}</b><small>${esc(warning.warningMessage)}</small></div></div>`).join("");
    $("evalContent").innerHTML = `<div class="done-item"><b>Tổng điểm</b><span class="grade">${esc(option.totalScore)}</span><span class="code">${option.optionRank ? `Hạng ${option.optionRank}` : "Phương án đã sửa"}</span></div>${rows || '<div class="empty-state">Backend chưa trả về chi tiết điểm cho phương án này.</div>'}${warningRows ? `<h3 style="margin-top:16px">Cảnh báo</h3>${warningRows}` : ""}`;
    openOverlay("evalOverlay");
  }

  async function openEditSection(sectionId, courseId) {
    const option = activeOption();
    if (!option) return;
    const item = (option.sections || []).find(section => Number(section.sectionId) === Number(sectionId));
    if (!item) return;
    editingSection = item;
    $("editSectionCourse").textContent = `${item.courseCode} · ${item.courseName} · lớp hiện tại ${item.sectionCode}`;
    let sectionCatalog = catalogSections;
    if (activeScheduleSemesterId && Number(activeScheduleSemesterId) !== Number(semester?.id)) {
      sectionCatalog = await api(`/api/academic/semesters/${activeScheduleSemesterId}/sections?courseId=${courseId}`);
    }
    const candidates = sectionCatalog.filter(section => Number(section.courseId) === Number(courseId)
      && Number(section.id) !== Number(sectionId) && section.sectionStatus === "OPEN"
      && Number(section.currentEnrollment) < Number(section.maxCapacity));
    $("editSectionSelect").innerHTML = candidates.length ? candidates.map(section => {
      const times = (section.meetings || []).map(meeting => `${days[Number(meeting.weekday) - 1] || `Thứ ${meeting.weekday}`}, tiết ${meeting.startPeriod}–${meeting.endPeriod}`).join(" · ");
      return `<option value="${section.id}">${esc(section.sectionCode)} · ${esc(times || "Chưa có lịch")}</option>`;
    }).join("") : `<option value="">Không có lớp khác còn chỗ</option>`;
    $("confirmEditSection").disabled = !candidates.length;
    openOverlay("editSectionOverlay");
  }

  async function applySectionEdit() {
    const option = activeOption();
    const newSectionId = Number($("editSectionSelect").value);
    if (!option || !editingSection || !newSectionId) return;
    const button = $("confirmEditSection");
    setBusy(button, true, "Đang kiểm tra...");
    try {
      const result = await api(`/api/schedules/options/${option.optionId}/replace-section`, {
        method: "POST",
        body: JSON.stringify({ oldSectionId: editingSection.sectionId, newSectionId, newOptionName: `${option.optionName} (Đã chỉnh sửa)` })
      });
      const compared = await api("/api/schedules/compare", {
        method: "POST", body: JSON.stringify({ optionIds: [option.optionId, result] })
      });
      const edited = compared.options.find(item => Number(item.optionId) === Number(result));
      if (!edited) throw new Error("Không tải được phương án đã chỉnh sửa.");
      options.push(edited);
      activeOptionId = edited.optionId;
      closeOverlay("editSectionOverlay");
      await loadWeekly(activeOptionId);
      toast("Đã kiểm tra xung đột và tạo phiên bản thời khóa biểu đã chỉnh sửa.");
    } catch (error) { toast(error.message); }
    finally { setBusy(button, false); }
  }

  async function uploadTranscript(file) {
    const form = new FormData();
    form.append("file", file);
    if (semester) form.append("semesterId", semester.id);
    try {
      const result = await api("/api/registration/academic-records/import", { method: "POST", body: form });
      const records = await api("/api/registration/academic-records/passed");
      passedRecords = Array.isArray(records) ? records : [];
      renderDoneCourses();
      const errors = result.errors && result.errors.length ? ` Có ${result.errors.length} dòng cần kiểm tra.` : "";
      toast(`Đã nhập bảng điểm: ${result.validRows || 0} dòng hợp lệ.${errors}`);
    } catch (error) { toast(error.message); }
  }

  $("schCourseSearch").addEventListener("input", renderStep1);
  $("manualCourseSearch").addEventListener("input", renderStep1);
  $("sourceCatalog").addEventListener("click", () => switchScheduleSource("catalog"));
  $("sourceManual").addEventListener("click", () => switchScheduleSource("manual"));
  $("btnAddManualCourse").addEventListener("click", () => openManualEditor());
  $("btnImportManualCourses").addEventListener("click", () => $("manualCoursesInput").click());
  $("manualCoursesInput").addEventListener("change", event => {
    const file = event.target.files && event.target.files[0];
    if (file) importManualCourses(file);
    event.target.value = "";
  });
  $("manualCourseList").addEventListener("change", event => {
    const checkbox = event.target.closest("input[data-manual-select]");
    if (!checkbox) return;
    if (checkbox.checked) selectedManualCourseIds.add(checkbox.dataset.manualSelect);
    else selectedManualCourseIds.delete(checkbox.dataset.manualSelect);
    renderStep1();
  });
  $("manualCourseList").addEventListener("click", event => {
    const edit = event.target.closest("button[data-edit-manual]");
    const remove = event.target.closest("button[data-delete-manual]");
    if (edit) openManualEditor(edit.dataset.editManual);
    if (remove) removeManualCourse(remove.dataset.deleteManual);
  });
  $("manualSectionList").addEventListener("click", event => {
    const addMeeting = event.target.closest("button[data-add-manual-meeting]");
    const removeSection = event.target.closest("button[data-remove-manual-section]");
    const removeMeeting = event.target.closest("button[data-remove-manual-meeting]");
    if (!manualDraft || !(addMeeting || removeSection || removeMeeting)) return;
    snapshotManualDraft();
    if (addMeeting) manualDraft.sections[Number(addMeeting.dataset.addManualMeeting)].meetings.push(blankManualMeeting());
    else if (removeSection) manualDraft.sections.splice(Number(removeSection.dataset.removeManualSection), 1);
    else if (removeMeeting) {
      const [sectionIndex, meetingIndex] = removeMeeting.dataset.removeManualMeeting.split(":").map(Number);
      manualDraft.sections[sectionIndex].meetings.splice(meetingIndex, 1);
    }
    renderManualEditor();
  });
  $("manualSectionList").addEventListener("input", event => {
    const input = event.target.closest("input[data-meeting-field]");
    if (!input) return;
    const row = input.closest(".manual-meeting-row");
    const startPeriod = row.querySelector('[data-meeting-field="startPeriod"]');
    const endPeriod = row.querySelector('[data-meeting-field="endPeriod"]');
    const startTime = row.querySelector('[data-meeting-field="startTime"]');
    const endTime = row.querySelector('[data-meeting-field="endTime"]');
    if (input === startTime || input === endTime) {
      const derived = periodsFromTimes(startTime.value, endTime.value);
      if (derived) { startPeriod.value = derived.startPeriod; endPeriod.value = derived.endPeriod; }
    } else {
      startTime.value = periodToTime(startPeriod.value);
      endTime.value = periodToTime(endPeriod.value, true);
    }
  });
  $("btnAddManualSection").addEventListener("click", () => {
    snapshotManualDraft();
    manualDraft.sections.push(blankManualSection(manualDraft.sections.length + 1));
    renderManualEditor();
  });
  $("saveManualCourse").addEventListener("click", saveManualCourse);
  $("closeManualCourse").addEventListener("click", closeManualEditor);
  $("cancelManualCourse").addEventListener("click", closeManualEditor);
  $("schCourseList").addEventListener("change", event => {
    const row = event.target.closest(".c-row");
    if (!row) return;
    const id = Number(row.dataset.id);
    if (event.target.checked) selectedCourseIds.add(id); else selectedCourseIds.delete(id);
    renderStep1();
  });
  $("schSelList").addEventListener("click", event => {
    const button = event.target.closest("button[data-rm]");
    const manualButton = event.target.closest("button[data-rm-manual]");
    if (button) selectedCourseIds.delete(Number(button.dataset.rm));
    if (manualButton) selectedManualCourseIds.delete(manualButton.dataset.rmManual);
    if (!button && !manualButton) return;
    renderStep1();
  });
  $("schTerm").addEventListener("change", event => loadSemesterData(Number(event.target.value)).catch(error => setStatus("Không tải được học kỳ", error.message, true)));
  $("toStep2").addEventListener("click", async () => {
    const button = $("toStep2");
    const hasSelection = scheduleSource === "manual" ? selectedManualCourseIds.size > 0 : selectedCourseIds.size > 0;
    if (!hasSelection) return toast("Vui lòng chọn ít nhất một học phần.");
    setBusy(button, true, "Đang tải hồ sơ và tiêu chí...");
    try {
      if (scheduleSource === "manual") {
        if (!$("minCr").value) $("minCr").value = "12";
        if (!$("maxCr").value) $("maxCr").value = "25";
        prerequisiteCheck = null;
        updateCreditRange();
        renderDoneCourses();
      } else await loadPlanSettings();
      setStep(2);
    }
    catch (error) { setStatus("Không tải được dữ liệu bước 2", error.message, true); toast(error.message); }
    finally { setBusy(button, false); }
  });
  $("minCr").addEventListener("input", updateCreditRange);
  $("maxCr").addEventListener("input", updateCreditRange);
  $("shiftSeg").addEventListener("click", event => {
    const button = event.target.closest("button[data-sh]");
    if (!button) return;
    document.querySelectorAll("#shiftSeg button").forEach(item => item.classList.toggle("active", item === button));
  });
  $("backStep1").addEventListener("click", () => setStep(1));
  $("backStep2").addEventListener("click", () => setStep(2));
  $("toStep3").addEventListener("click", event => createSchedule(event.currentTarget));
  document.querySelectorAll("#schStepper .st-item").forEach(item => item.addEventListener("click", () => {
    const target = Number(item.dataset.step);
    if (target < 3) setStep(target);
  }));
  $("optList").addEventListener("click", event => {
    const card = event.target.closest(".opt-card");
    if (!card) return;
    activeOptionId = Number(card.dataset.oid);
    weeklySchedule = null;
    renderOptions();
    loadWeekly(activeOptionId);
  });
  $("ttBody").addEventListener("click", event => {
    const button = event.target.closest("button[data-edit-section]");
    if (button) openEditSection(Number(button.dataset.editSection), Number(button.dataset.course)).catch(error => toast(error.message));
  });
  $("btnDetailEval").addEventListener("click", openEvaluation);
  $("btnExportPdf").addEventListener("click", () => downloadExport("PDF").catch(error => toast(error.message)));
  $("btnExportPng").addEventListener("click", () => downloadExport("PNG").catch(error => toast(error.message)));
  $("btnSavePlan").addEventListener("click", () => {
    const shift = document.querySelector("#shiftSeg button.active")?.textContent.trim() || "thời khóa biểu";
    $("planAliasInput").value = `Lịch ${shift} · ${semester ? semester.semesterCode : "SmartEdu"}`;
    openOverlay("saveTtOverlay");
  });
  $("confirmSaveTt").addEventListener("click", saveSchedule);
  $("closeSaveTt").addEventListener("click", () => closeOverlay("saveTtOverlay"));
  $("cancelSaveTt").addEventListener("click", () => closeOverlay("saveTtOverlay"));
  $("btnSavedSchedules").addEventListener("click", showSavedSchedules);
  $("closeSavedSchedules").addEventListener("click", () => closeOverlay("savedSchedulesOverlay"));
  $("savedSchedulesList").addEventListener("click", handleSavedAction);
  $("openCompare").addEventListener("click", openCompare);
  $("closeCompare").addEventListener("click", () => closeOverlay("compareOverlay"));
  $("cmpGrid").addEventListener("change", event => {
    const checkbox = event.target.closest("input[data-compare]");
    if (!checkbox) return;
    const id = Number(checkbox.dataset.compare);
    if (checkbox.checked) {
      if (selectedForCompare.size >= 3) { checkbox.checked = false; return toast("Có thể so sánh tối đa 3 phương án."); }
      selectedForCompare.add(id);
    } else selectedForCompare.delete(id);
  });
  $("cmpGrid").addEventListener("click", event => {
    if (event.target.closest("#runCompare")) return compareSelected();
    const pick = event.target.closest("button[data-pick]");
    if (!pick) return;
    activeOptionId = Number(pick.dataset.pick);
    closeOverlay("compareOverlay");
    renderOptions();
    loadWeekly(activeOptionId);
  });
  $("prevWeek").addEventListener("click", () => { if (weekIndex > 0) { weekIndex--; updateWeekLabel(); } });
  $("nextWeek").addEventListener("click", () => {
    if (!scheduleRange || !scheduleRange.startDate || !scheduleRange.endDate) { weekIndex++; updateWeekLabel(); return; }
    const start = new Date(`${scheduleRange.startDate}T00:00:00`);
    const nextEnd = new Date(start);
    nextEnd.setDate(nextEnd.getDate() + (weekIndex + 1) * 7 + 6);
    if (nextEnd <= new Date(`${scheduleRange.endDate}T00:00:00`)) { weekIndex++; updateWeekLabel(); }
  });
  $("btnUploadTranscript").addEventListener("click", () => $("transcriptInput").click());
  $("transcriptInput").addEventListener("change", event => {
    const file = event.target.files && event.target.files[0];
    if (file) uploadTranscript(file);
    event.target.value = "";
  });
  $("closeEditSection").addEventListener("click", () => closeOverlay("editSectionOverlay"));
  $("cancelEditSection").addEventListener("click", () => closeOverlay("editSectionOverlay"));
  $("confirmEditSection").addEventListener("click", applySectionEdit);
  $("closeEval").addEventListener("click", () => closeOverlay("evalOverlay"));
  ["saveTtOverlay", "compareOverlay", "savedSchedulesOverlay", "editSectionOverlay", "evalOverlay", "manualCourseOverlay"].forEach(id => {
    $(id).addEventListener("mousedown", event => { if (event.target === $(id)) closeOverlay(id); });
  });
  initializeSchedule();
});
